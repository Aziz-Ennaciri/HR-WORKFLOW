package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.handler.NodeHandler;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.execution.service.INodeInstanceService;
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
    private final INodeInstanceService nodeInstanceService;
    private final WorkflowInstancerepository workflowInstanceRepository;
    private final WorkflowRepository workflowRepository;
    private final NodeRepository nodeRepository;
    private final NodeInstanceRepository nodeInstanceRepository;
    
    // ✅ Spring injects ALL NodeHandler implementations
    private final List<NodeHandler> nodeHandlers;
    
    // ✅ Handler registry - built at startup
    private Map<NodeType, NodeHandler> handlerRegistry;

    /**
     * Build handler registry after Spring initialization
     */
    @PostConstruct
    public void init() {
        handlerRegistry = new HashMap<>();
        
        for (NodeHandler handler : nodeHandlers) {
            handlerRegistry.put(handler.getType(), handler);
            log.info("✅ Registered handler for node type: {}", handler.getType());
        }
        
        log.info("🚀 NodeHandler registry initialized with {} handlers", handlerRegistry.size());
    }

    @Override
    @Transactional
    public WorkflowInstance triggerWorkflow(TriggerWorkflowInstanceDTO dto, User user) {
        log.info("🎯 Triggering workflow: {} by user: {}", dto.getWorkflowId(), user.getEmail());
        
        Workflow workflow = validateWorkflow(dto.getWorkflowId());
        WorkflowInstance workflowInstance = workflowInstanceService.createInstance(dto, user);
        continueExecution(workflowInstance.getId());
        
        return workflowInstance;
    }

    @Override
    @Transactional
    public void continueExecution(Long workflowInstanceId) {
        log.info("▶️  Continuing execution: {}", workflowInstanceId);
        
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found"));

        try {
            if (instance.getStatus() == ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus.PENDING) {
                instance = workflowInstanceService.startInstance(workflowInstanceId);
            }

            List<Node> nodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(
                instance.getWorkflow().getId());

            if (nodes.isEmpty()) {
                throw new RuntimeException("Workflow has no nodes");
            }

            log.info("📋 Executing {} nodes", nodes.size());

            String previousOutput = instance.getInputData();
            
            for (Node node : nodes) {
                log.info("🔄 Node {} of {}: {}", 
                    node.getOrderIndex() + 1, nodes.size(), node.getType());

                NodeInstance nodeInstance = executeNode(instance, node, previousOutput);
                instance.setCurrentNode(node);

                if (nodeInstance.getStatus() == NodeInstanceStatus.FAILED) {
                    workflowInstanceService.failInstance(instance.getId(),
                        "Failed at: " + node.getType(), 
                        nodeInstance.getErrorMessage());
                    return;
                }

                if (nodeInstance.getStatus() == NodeInstanceStatus.REJECTED) {
                    workflowInstanceService.failInstance(instance.getId(),
                        "Rejected at: " + node.getType(), 
                        nodeInstance.getComment());
                    return;
                }

                previousOutput = nodeInstance.getOutputData();
            }

            workflowInstanceService.completeInstance(instance.getId(), previousOutput);
            log.info("🎉 Workflow completed successfully");

        } catch (Exception e) {
            log.error("💥 Execution error", e);
            workflowInstanceService.failInstance(instance.getId(),
                "Error: " + e.getMessage(), getStackTrace(e));
        }
    }

    private NodeInstance executeNode(WorkflowInstance instance, Node node, String inputData) {
        NodeInstance nodeInstance = nodeInstanceRepository
                .findByWorkflowInstanceIdAndNodeId(instance.getId(), node.getId())
                .orElseGet(() -> createNodeInstance(instance, node, inputData));

        if (nodeInstance.getStatus() == NodeInstanceStatus.COMPLETED ||
            nodeInstance.getStatus() == NodeInstanceStatus.REJECTED) {
            return nodeInstance;
        }

        try {
            nodeInstance = nodeInstanceService.startNode(nodeInstance);

            // ✅ Get handler from registry (NO switch-case!)
            NodeHandler handler = getHandlerForNode(node);
            String result = handler.execute(node, nodeInstance);

            nodeInstance.markCompleted(result);
            nodeInstanceRepository.save(nodeInstance);

            return nodeInstance;

        } catch (Exception e) {
            log.error("❌ Node failed", e);
            return nodeInstanceService.failNode(nodeInstance.getId(), e.getMessage());
        }
    }

    private NodeInstance createNodeInstance(WorkflowInstance instance, Node node, String inputData) {
        NodeInstance nodeInstance = new NodeInstance();
        nodeInstance.setWorkflowInstance(instance);
        nodeInstance.setNode(node);
        nodeInstance.setExecutionOrder(node.getOrderIndex());
        nodeInstance.setStatus(NodeInstanceStatus.PENDING);
        nodeInstance.setInputData(inputData);
        
        return nodeInstanceRepository.save(nodeInstance);
    }

    /**
     * ✅ Get handler from registry - NO SWITCH-CASE!
     */
    private NodeHandler getHandlerForNode(Node node) {
        NodeHandler handler = handlerRegistry.get(node.getType());
        
        if (handler == null) {
            throw new UnsupportedOperationException(
                "No handler for: " + node.getType());
        }
        
        return handler;
    }

    private Workflow validateWorkflow(Long workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
            .orElseThrow(() -> new RuntimeException("Workflow not found"));

        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new RuntimeException("Only ACTIVE workflows can be executed");
        }

        return workflow;
    }

    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }
}