package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.handler.NodeHandler;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowExecutionService;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowInstanceService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowExecutionServiceImpl implements IWorkflowExecutionService {

    private final IWorkflowInstanceService workflowInstanceService;
    private final WorkflowRepository workflowRepository;
    private final NodeRepository nodeRepository;
    private final NodeInstanceRepository nodeInstanceRepository;

    private final ExecutionTransactionHelper txHelper;

    private final List<NodeHandler> nodeHandlers;

    private Map<NodeType, NodeHandler> handlerRegistry;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    @PostConstruct
    public void init() {
        handlerRegistry = new HashMap<>();
        for (NodeHandler handler : nodeHandlers) {
            handlerRegistry.put(handler.getType(), handler);
            log.info("✅ Registered handler for node type: {}", handler.getType());
        }
        log.info("🚀 NodeHandler registry initialized with {} handlers", handlerRegistry.size());
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }


    @Override
    @Transactional
    public WorkflowInstance triggerWorkflow(TriggerWorkflowInstanceDTO dto, User user) {
        log.info("🎯 Triggering workflow: {} by user: {}", dto.getWorkflowId(), user.getEmail());

        Workflow workflow = validateWorkflow(dto.getWorkflowId());
        WorkflowInstance workflowInstance = workflowInstanceService.createInstance(dto, user);

        List<Node> nodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflow.getId());
        String inputData = workflowInstance.getInputData();
        for (Node node : nodes) {
            NodeInstance ni = new NodeInstance();
            ni.setWorkflowInstance(workflowInstance);
            ni.setNode(node);
            ni.setExecutionOrder(node.getOrderIndex());
            ni.setStatus(NodeInstanceStatus.PENDING);
            ni.setInputData(node.getOrderIndex() == 0 ? inputData : null);
            nodeInstanceRepository.save(ni);
        }

        final Long instanceId = workflowInstance.getId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                executor.submit(() -> {
                    try {
                        log.info("🚀 Post-commit: starting execution {}", instanceId);
                        continueExecution(instanceId);
                    } catch (Exception e) {
                        log.error("💥 Background execution failed: {}", instanceId, e);
                    }
                });
            }
        });

        return workflowInstance;
    }

    @Override
    public void continueExecution(Long workflowInstanceId) {
        log.info("▶️  Continuing execution: {}", workflowInstanceId);

        try {
            txHelper.startInstance(workflowInstanceId);

            List<Node> nodes = txHelper.getWorkflowNodes(workflowInstanceId);
            if (nodes.isEmpty()) {
                throw new RuntimeException("Workflow has no nodes");
            }

            log.info("📋 Executing {} nodes", nodes.size());
            String previousOutput = txHelper.getInstanceInputData(workflowInstanceId);

            for (Node node : nodes) {
                log.info("🔄 Node {} of {}: {}",
                        node.getOrderIndex() + 1, nodes.size(), node.getType());

                NodeHandler handler = getHandlerForNode(node);

                NodeInstance nodeInstance = txHelper.executeNode(
                        workflowInstanceId, node.getId(), previousOutput, handler);

                txHelper.updateCurrentNode(workflowInstanceId, node.getId());

                if (nodeInstance.getStatus() == NodeInstanceStatus.WAITING_APPROVAL) {
                    log.info("⏸️  Workflow PAUSED at APPROVAL node — waiting for human decision");
                    txHelper.pauseInstance(workflowInstanceId);
                    return; // Stop the loop — will resume when approve/reject is called
                }

                if (nodeInstance.getStatus() == NodeInstanceStatus.FAILED) {
                    txHelper.failInstance(workflowInstanceId,
                            "Failed at: " + node.getType(),
                            nodeInstance.getErrorMessage());
                    return;
                }

                if (nodeInstance.getStatus() == NodeInstanceStatus.REJECTED) {
                    txHelper.failInstance(workflowInstanceId,
                            "Rejected at: " + node.getType(),
                            nodeInstance.getComment());
                    return;
                }

                previousOutput = nodeInstance.getOutputData();
            }

            txHelper.completeInstance(workflowInstanceId, previousOutput);
            log.info("🎉 Workflow completed successfully");

        } catch (Exception e) {
            log.error("💥 Execution error", e);
            try {
                txHelper.failInstance(workflowInstanceId,
                        "Error: " + e.getMessage(), getStackTrace(e));
            } catch (Exception e2) {
                log.error("💥 Failed to mark instance as failed", e2);
            }
        }
    }

    public void resumeAfterApproval(Long workflowInstanceId) {
        log.info("▶️  Resuming workflow {} after approval", workflowInstanceId);
        executor.submit(() -> {
            try {
                continueExecution(workflowInstanceId);
            } catch (Exception e) {
                log.error("💥 Resume failed: {}", workflowInstanceId, e);
            }
        });
    }


    private NodeHandler getHandlerForNode(Node node) {
        NodeHandler handler = handlerRegistry.get(node.getType());
        if (handler == null) {
            throw new RuntimeException("No handler registered for node type: " + node.getType());
        }
        return handler;
    }

    private Workflow validateWorkflow(Long workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Workflow is not active");
        }
        return workflow;
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 2000) break;
        }
        return sb.toString();
    }
}