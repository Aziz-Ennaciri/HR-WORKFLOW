package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.execution.handler.ApprovalNodeHandler;
import ma.rh.ai.hr_workflow.execution.handler.NodeHandler;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;


@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionTransactionHelper {

    private final WorkflowInstancerepository workflowInstanceRepository;
    private final NodeInstanceRepository nodeInstanceRepository;
    private final NodeRepository nodeRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startInstance(Long workflowInstanceId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        if (instance.getStatus() == WorkflowInstanceStatus.PENDING) {
            instance.start();
            workflowInstanceRepository.save(instance);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Node> getWorkflowNodes(Long workflowInstanceId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        return nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(instance.getWorkflow().getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public String getInstanceInputData(Long workflowInstanceId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        return instance.getInputData();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCurrentNode(Long workflowInstanceId, Long nodeId) {
        try {
            WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                    .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
            Node node = nodeRepository.findById(nodeId)
                    .orElseThrow(() -> new RuntimeException("Node not found"));
            instance.setCurrentNode(node);
            workflowInstanceRepository.save(instance);
        } catch (ObjectOptimisticLockingFailureException ex) {
            WorkflowInstance fresh = workflowInstanceRepository.findById(workflowInstanceId).orElse(null);
            if (fresh == null
                    || fresh.getStatus() == WorkflowInstanceStatus.CANCELLED
                    || fresh.getStatus() == WorkflowInstanceStatus.FAILED) {
                log.warn("⚠️  updateCurrentNode: instance {} is {} — ignoring locking conflict",
                        workflowInstanceId, fresh == null ? "gone" : fresh.getStatus());
                return;
            }
            throw ex;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeInstance executeNode(Long workflowInstanceId, Long nodeId, String inputData,
                                    NodeHandler handler) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new RuntimeException("Node not found"));

        instance.getWorkflow().getName();
        instance.getWorkflow().getWorkflowKey();

        NodeInstance nodeInstance = nodeInstanceRepository
                .findByWorkflowInstanceIdAndNodeId(instance.getId(), node.getId())
                .orElseGet(() -> {
                    NodeInstance ni = new NodeInstance();
                    ni.setWorkflowInstance(instance);
                    ni.setNode(node);
                    ni.setExecutionOrder(node.getOrderIndex());
                    ni.setStatus(NodeInstanceStatus.PENDING);
                    ni.setInputData(inputData);
                    return nodeInstanceRepository.save(ni);
                });

        nodeInstance.setWorkflowInstance(instance);

        if (nodeInstance.getInputData() == null) {
            nodeInstance.setInputData(inputData);
        }

        if (nodeInstance.getStatus() == NodeInstanceStatus.COMPLETED ||
                nodeInstance.getStatus() == NodeInstanceStatus.REJECTED) {
            return nodeInstance;
        }

        try {
            nodeInstance.start();
            nodeInstanceRepository.saveAndFlush(nodeInstance);

            String result = handler.execute(node, nodeInstance);

            if (ApprovalNodeHandler.APPROVAL_SIGNAL.equals(result)) {
                log.info("⏸️  Node {} is APPROVAL — marking WAITING_APPROVAL", node.getType());
                nodeInstance.setStatus(NodeInstanceStatus.WAITING_APPROVAL);
                nodeInstanceRepository.save(nodeInstance);
                return nodeInstance;
            }

            nodeInstance.markCompleted(result);
            nodeInstanceRepository.save(nodeInstance);
            return nodeInstance;

        } catch (Exception e) {
            log.error("❌ Node {} failed: {}", node.getType(), e.getMessage());
            nodeInstance.markFailed(e.getMessage());
            nodeInstanceRepository.save(nodeInstance);
            return nodeInstance;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void pauseInstance(Long workflowInstanceId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        instance.setStatus(WorkflowInstanceStatus.PAUSED);
        workflowInstanceRepository.save(instance);
        log.info("⏸️  Workflow instance {} PAUSED for approval", workflowInstanceId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeInstance(Long workflowInstanceId, String outputData) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        instance.setStatus(WorkflowInstanceStatus.COMPLETED);
        instance.setOutputData(outputData);
        instance.complete();
        workflowInstanceRepository.save(instance);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetForRetry(Long executionId, Long nodeId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(executionId)
                .orElseThrow(() -> new RuntimeException("Execution not found"));

        NodeInstance nodeInstance = nodeInstanceRepository
                .findByWorkflowInstanceIdAndNodeId(executionId, nodeId)
                .orElseThrow(() -> new RuntimeException("Node instance not found"));

        if (nodeInstance.getStatus() != NodeInstanceStatus.FAILED) {
            throw new IllegalStateException("Node is not in FAILED state");
        }

        nodeInstance.setStatus(NodeInstanceStatus.PENDING);
        nodeInstance.setErrorMessage(null);
        nodeInstance.setStartedAt(null);
        nodeInstance.setFinishedAt(null);
        nodeInstance.setDurationMs(null);
        nodeInstanceRepository.save(nodeInstance);

        instance.setStatus(WorkflowInstanceStatus.PENDING);
        instance.setErrorMessage(null);
        instance.setErrorStackTrace(null);
        instance.setFinishedAt(null);
        instance.setDurationMs(null);
        workflowInstanceRepository.save(instance);
        log.info("🔄 Reset execution {} node {} for retry", executionId, nodeId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInstance(Long workflowInstanceId, String errorMessage, String stackTrace) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId).orElse(null);
        if (instance == null) {
            log.warn("⚠️  failInstance: workflow instance {} not found, skipping", workflowInstanceId);
            return;
        }
        instance.setStatus(WorkflowInstanceStatus.FAILED);
        instance.setErrorMessage(errorMessage);
        instance.setErrorStackTrace(stackTrace);
        instance.complete();
        workflowInstanceRepository.save(instance);
    }
}