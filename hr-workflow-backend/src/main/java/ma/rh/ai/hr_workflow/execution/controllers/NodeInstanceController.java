package ma.rh.ai.hr_workflow.execution.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.DTOs.*;
import ma.rh.ai.hr_workflow.execution.mappers.NodeInstanceMapper;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.execution.service.INodeInstanceService;
import ma.rh.ai.hr_workflow.execution.service.Impl.ExecutionTransactionHelper;
import ma.rh.ai.hr_workflow.execution.service.Impl.WorkflowExecutionServiceImpl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/executions/nodes")
@RequiredArgsConstructor
@Tag(name = "Node Instance")
public class NodeInstanceController {

    private final INodeInstanceService nodeInstanceService;
    private final NodeInstanceRepository nodeInstanceRepository;
    private final NodeInstanceMapper nodeInstanceMapper;
    private final WorkflowExecutionServiceImpl executionService;
    private final WorkflowInstancerepository workflowInstanceRepository;
    private final ExecutionTransactionHelper txHelper;
    private final ObjectMapper objectMapper;

    @GetMapping("/{id}")
    @Operation(summary = "Get node instance")
    public ResponseEntity<NodeInstanceResponseDTO> getNode(@PathVariable Long id) {
        NodeInstance node = nodeInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found"));
        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(node));
    }

    @GetMapping("/workflow-instance/{instanceId}")
    @Operation(summary = "Get all nodes for instance")
    public ResponseEntity<List<NodeInstanceResponseDTO>> getNodes(
            @PathVariable Long instanceId) {
        List<NodeInstance> nodes = nodeInstanceRepository
                .findByWorkflowInstanceIdOrderByExecutionOrderAsc(instanceId);
        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(nodes));
    }

    @GetMapping("/my-approvals")
    @Operation(summary = "Get pending approvals for the current user")
    public ResponseEntity<List<PendingApprovalDTO>> getMyApprovals(
            @RequestParam Long userId) {

        List<NodeInstance> pendingNodes = nodeInstanceRepository
                .findByAssignedToIdAndStatusOrderByCreatedAtDesc(userId, NodeInstanceStatus.WAITING_APPROVAL);

        List<PendingApprovalDTO> approvals = new ArrayList<>();
        for (NodeInstance ni : pendingNodes) {
            PendingApprovalDTO dto = new PendingApprovalDTO();
            dto.setNodeInstanceId(ni.getId());
            dto.setStatus(ni.getStatus().name());
            dto.setInputData(ni.getInputData());
            dto.setCreatedAt(ni.getCreatedAt());

            // Extract instructions from node config
            try {
                String configJson = ni.getNode().getConfigJson();
                if (configJson != null) {
                    JsonNode config = objectMapper.readTree(configJson);
                    if (config.has("instructions")) {
                        dto.setInstructions(config.get("instructions").asText());
                    }
                }
            } catch (Exception ignored) {}

            WorkflowInstance wi = ni.getWorkflowInstance();
            dto.setWorkflowInstanceId(wi.getId());
            dto.setWorkflowInstanceStatus(wi.getStatus().name());

            dto.setWorkflowId(wi.getWorkflow().getId());
            dto.setWorkflowName(wi.getWorkflow().getName());

            dto.setTriggeredById(wi.getTriggeredBy().getId());
            dto.setTriggeredByEmail(wi.getTriggeredBy().getEmail());
            dto.setTriggeredByName(wi.getTriggeredBy().getFirstName() + " " + wi.getTriggeredBy().getLastName());

            approvals.add(dto);
        }

        return ResponseEntity.ok(approvals);
    }

    @GetMapping("/my-approvals/count")
    @Operation(summary = "Count pending approvals for the current user")
    public ResponseEntity<Long> getMyApprovalsCount(@RequestParam Long userId) {
        long count = nodeInstanceRepository.countByAssignedToIdAndStatus(userId, NodeInstanceStatus.WAITING_APPROVAL);
        return ResponseEntity.ok(count);
    }

    // ─── Approve / Reject ─────────────────────────────────────────
    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve node and resume workflow")
    public ResponseEntity<NodeInstanceResponseDTO> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveNodeDTO dto) {

        NodeInstance nodeInstance = nodeInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Node instance not found"));

        // Enforce: only the assigned approver (or any admin if unassigned) can approve
        validateApprover(nodeInstance, dto.getActor());

        NodeInstance node = nodeInstanceService.approveNode(id, dto);

        Long workflowInstanceId = node.getWorkflowInstance().getId();
        WorkflowInstance wfInstance = workflowInstanceRepository.findById(workflowInstanceId)
                .orElse(null);

        if (wfInstance != null && wfInstance.getStatus() == WorkflowInstanceStatus.PAUSED) {
            executionService.resumeAfterApproval(workflowInstanceId);
        }

        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(node));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject node and stop workflow")
    public ResponseEntity<NodeInstanceResponseDTO> reject(
            @PathVariable Long id,
            @Valid @RequestBody ApproveNodeDTO dto) {

        NodeInstance nodeInstance = nodeInstanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Node instance not found"));

        // Enforce: only the assigned approver (or any admin if unassigned) can reject
        validateApprover(nodeInstance, dto.getActor());

        NodeInstance node = nodeInstanceService.rejectNode(id, dto);
        Long workflowInstanceId = node.getWorkflowInstance().getId();
        txHelper.failInstance(workflowInstanceId,
                "Rejected at APPROVAL by " + dto.getActor(),
                dto.getComment());

        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(node));
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry failed node")
    public ResponseEntity<NodeInstanceResponseDTO> retry(@PathVariable Long id) {
        NodeInstance node = nodeInstanceService.retryNode(id);
        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(node));
    }

    private void validateApprover(NodeInstance nodeInstance, String actorEmail) {
        if (nodeInstance.getAssignedTo() == null) {
            return;
        }

        String assignedEmail = nodeInstance.getAssignedTo().getEmail();
        if (!assignedEmail.equalsIgnoreCase(actorEmail)) {
            throw new RuntimeException(
                    "Only the assigned approver (" + assignedEmail + ") can approve/reject this node");
        }
    }
}