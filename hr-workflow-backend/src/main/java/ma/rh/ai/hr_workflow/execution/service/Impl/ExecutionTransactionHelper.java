package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.execution.handler.NodeHandler;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;

/**
 * All methods here use REQUIRES_NEW so each gets its own transaction,
 * committed immediately. Because they're called from a DIFFERENT bean
 * (WorkflowExecutionServiceImpl), Spring's proxy properly intercepts them.
 */
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
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new RuntimeException("Node not found"));
        instance.setCurrentNode(node);
        workflowInstanceRepository.save(instance);
    }

    /**
     * Execute a single node in its own transaction.
     * Each status change is committed immediately and visible to polling frontend.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public NodeInstance executeNode(Long workflowInstanceId, Long nodeId, String inputData,
                                    NodeHandler handler) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        Node node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new RuntimeException("Node not found"));

        // Force-initialize lazy proxies so handlers can access them freely
        instance.getWorkflow().getName();
        instance.getWorkflow().getWorkflowKey();

        // Find pre-created node instance or create new one
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

        // Attach fully-initialized instance so handlers don't hit lazy errors
        nodeInstance.setWorkflowInstance(instance);

        if (nodeInstance.getInputData() == null) {
            nodeInstance.setInputData(inputData);
        }

        if (nodeInstance.getStatus() == NodeInstanceStatus.COMPLETED ||
                nodeInstance.getStatus() == NodeInstanceStatus.REJECTED) {
            return nodeInstance;
        }

        try {
            // Mark IN_PROGRESS — flush so polling sees it immediately
            nodeInstance.start();
            nodeInstanceRepository.saveAndFlush(nodeInstance);

            // Run the actual handler (DRIVE, GPT, EXCEL, EMAIL)
            String result = handler.execute(node, nodeInstance);

            // Mark COMPLETED
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
    public void completeInstance(Long workflowInstanceId, String outputData) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        instance.setStatus(WorkflowInstanceStatus.COMPLETED);
        instance.setOutputData(outputData);
        instance.complete();
        workflowInstanceRepository.save(instance);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInstance(Long workflowInstanceId, String errorMessage, String stackTrace) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));
        instance.setStatus(WorkflowInstanceStatus.FAILED);
        instance.setErrorMessage(errorMessage);
        instance.setErrorStackTrace(stackTrace);
        instance.complete();
        workflowInstanceRepository.save(instance);
    }
}