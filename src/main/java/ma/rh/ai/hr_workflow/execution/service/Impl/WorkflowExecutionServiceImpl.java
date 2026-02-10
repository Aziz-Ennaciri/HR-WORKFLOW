package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.util.List;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
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
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;

@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl implements IWorkflowExecutionService{
    private final IWorkflowInstanceService workflowInstanceService;
    private final INodeInstanceService nodeInstanceService;
    private final WorkflowInstancerepository workflowInstanceRepository;
    private final WorkflowRepository workflowRepository;
    private final NodeRepository nodeRepository;
    private final NodeInstanceRepository nodeInstanceRepository;

    @Override
    @Transactional
    public WorkflowInstance triggerWorkflow(TriggerWorkflowInstanceDTO dto, User user) {
        Workflow workflow = validateWorkflow(dto.getWorkflowId());
        WorkflowInstance workflowInstance = workflowInstanceService.createInstance(dto, user);
        continueExecution(workflowInstance.getId());
        return workflowInstance;
    }

    @Override
    @Transactional
    public void continueExecution(Long workflowInstanceId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElseThrow(() -> new RuntimeException("Workflow instance not found with id: " + workflowInstanceId));

        try {
            // Start instance if it's PENDING
            if (instance.getStatus() == ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus.PENDING) {
                instance = workflowInstanceService.startInstance(workflowInstanceId);
            }

            // Get all nodes in order
            List<Node> nodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(instance.getWorkflow().getId());

            if (nodes.isEmpty()) {
                throw new RuntimeException("Workflow has no nodes to execute");
            }

            // Execute nodes in sequence
            for (Node node : nodes) {
                NodeInstance nodeInstance = executeNode(instance, node);

                // Update current node in workflow instance
                instance.setCurrentNode(node);

                // If node failed or was rejected, stop execution
                if (nodeInstance.getStatus() == NodeInstanceStatus.FAILED) {
                    workflowInstanceService.failInstance(
                        instance.getId(),
                        "Node execution failed at: " + node.getType(),
                        nodeInstance.getErrorMessage()
                    );
                    return;
                }

                if (nodeInstance.getStatus() == NodeInstanceStatus.REJECTED) {
                    workflowInstanceService.failInstance(
                        instance.getId(),
                        "Node was rejected at: " + node.getType(),
                        "Rejected by: " + nodeInstance.getActor() + ". Comment: " + nodeInstance.getComment()
                    );
                    return;
                }
            }

            // All nodes completed successfully
            workflowInstanceService.completeInstance(instance.getId(), "Workflow completed successfully");

        } catch (Exception e) {
            workflowInstanceService.failInstance(
                instance.getId(),
                "Execution error: " + e.getMessage(),
                getStackTrace(e)
            );
        }
    }

    /**
     * Execute a single node
     */
    private NodeInstance executeNode(WorkflowInstance instance, Node node) {

        // Check if node instance already exists (for retries or continuation)
        NodeInstance nodeInstance = nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(instance.getId(), node.getId())
                .orElseGet(() -> createNodeInstance(instance, node));

        // If already completed or rejected, skip
        if (nodeInstance.getStatus() == NodeInstanceStatus.COMPLETED ||
            nodeInstance.getStatus() == NodeInstanceStatus.REJECTED) {
            return nodeInstance;
        }

        try {
            // Start the node
            nodeInstance = nodeInstanceService.startNode(nodeInstance);

            // Execute based on node type
            String result = executeNodeLogic(node, nodeInstance);

            // Mark as completed
            nodeInstance.markCompleted(result);
            nodeInstanceRepository.save(nodeInstance);

            return nodeInstance;

        } catch (Exception e) {
            nodeInstance = nodeInstanceService.failNode(nodeInstance.getId(), e.getMessage());
            return nodeInstance;
        }
    }

    /**
     * Create a new node instance
     */
    private NodeInstance createNodeInstance(WorkflowInstance instance, Node node) {
        NodeInstance nodeInstance = new NodeInstance();
        nodeInstance.setWorkflowInstance(instance);
        nodeInstance.setNode(node);
        nodeInstance.setExecutionOrder(node.getOrderIndex());
        nodeInstance.setStatus(NodeInstanceStatus.PENDING);
        nodeInstance.setInputData(instance.getInputData());  // Pass workflow input to first node
        
        return nodeInstanceRepository.save(nodeInstance);
    }

    /**
     * Execute node logic based on type
     */
    private String executeNodeLogic(Node node, NodeInstance nodeInstance) {
        // For now, return a placeholder
        // In real implementation, you would:
        // 1. Get the appropriate NodeHandler based on node.getType()
        // 2. Call handler.execute(node, nodeInstance)
        // 3. Return the result

        switch (node.getType()) {
            case EMAIL:
                return executeEmailNode(node, nodeInstance);
            case GPT:
                return executeGptNode(node, nodeInstance);
            case DRIVE:
                return executeDriveNode(node, nodeInstance);
            case EXCEL:
                return executeExcelNode(node, nodeInstance);
            default:
                throw new UnsupportedOperationException("Node type not supported: " + node.getType());
        }
    }


    private String executeEmailNode(Node node, NodeInstance nodeInstance) {
        // Placeholder - replace with actual email handling logic
        return "{\"status\": \"email_sent\", \"message\": \"Email sent successfully\"}";
    }

    private String executeGptNode(Node node, NodeInstance nodeInstance) {
        // Placeholder - replace with actual GPT API call
        return "{\"status\": \"analysis_complete\", \"result\": \"GPT analysis result\"}";
    }

    private String executeDriveNode(Node node, NodeInstance nodeInstance) {
        // Placeholder - replace with actual Google Drive logic
        return "{\"status\": \"file_saved\", \"fileId\": \"abc123\"}";
    }

    private String executeExcelNode(Node node, NodeInstance nodeInstance) {
        // Placeholder - replace with actual Excel processing
        return "{\"status\": \"excel_processed\", \"rows\": 100}";
    }

    /**
     * Validate workflow can be executed
     */
    private Workflow validateWorkflow(Long workflowId) {
    Workflow workflow = workflowRepository.findById(workflowId)
        .orElseThrow(() -> new RuntimeException(
            "Workflow not found with id: " + workflowId));

    if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
        throw new RuntimeException(
            "Only ACTIVE workflows can be executed. Current status: " + workflow.getStatus());
    }

    return workflow;
}

    /**
     * Get stack trace as string
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append(element.toString()).append("\n");
        }
        return sb.toString();
    }
    
}
