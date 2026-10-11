package ma.rh.ai.hr_workflow.execution.controllers;

import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.config.AccessGuard;
import ma.rh.ai.hr_workflow.config.SecurityUtils;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Slf4j
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
    private final AccessGuard accessGuard;

    @GetMapping("/{id}")
    @PreAuthorize("@access.canAccessNodeInstance(#id)")
    @Operation(summary = "Get node instance")
    public ResponseEntity<NodeInstanceResponseDTO> getNode(@PathVariable Long id) {
        NodeInstance node = nodeInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Node instance not found"));
        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(node));
    }

    @GetMapping("/workflow-instance/{instanceId}")
    @PreAuthorize("@access.canAccessExecution(#instanceId)")
    @Operation(summary = "Get all nodes for instance")
    public ResponseEntity<List<NodeInstanceResponseDTO>> getNodes(
            @PathVariable Long instanceId) {
        List<NodeInstance> nodes = nodeInstanceRepository
                .findByWorkflowInstanceIdOrderByExecutionOrderAsc(instanceId);
        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(nodes));
    }

    @GetMapping("/my-approvals")
    @Operation(summary = "Get pending approvals for the current user")
    public ResponseEntity<List<PendingApprovalDTO>> getMyApprovals() {

        List<NodeInstance> pendingNodes = nodeInstanceRepository
                .findByAssignedToEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                        SecurityUtils.currentUserEmail(), NodeInstanceStatus.WAITING_APPROVAL);

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
            } catch (Exception e) {
                log.warn("Could not read approval instructions for node instance {}: {}", ni.getId(), e.getMessage());
            }

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
    public ResponseEntity<Long> getMyApprovalsCount() {
        long count = nodeInstanceRepository.countByAssignedToEmailIgnoreCaseAndStatus(
                SecurityUtils.currentUserEmail(), NodeInstanceStatus.WAITING_APPROVAL);
        return ResponseEntity.ok(count);
    }

    // ─── Approve / Reject ─────────────────────────────────────────
    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve node and resume workflow")
    public ResponseEntity<NodeInstanceResponseDTO> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveNodeDTO dto) {

        NodeInstance nodeInstance = nodeInstanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Node instance not found"));

        validateApprover(nodeInstance);
        // The actor is always the authenticated user, never a value supplied by the client
        dto.setActor(SecurityUtils.currentUserEmail());

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
                .orElseThrow(() -> new ResourceNotFoundException("Node instance not found"));

        validateApprover(nodeInstance);
        dto.setActor(SecurityUtils.currentUserEmail());

        NodeInstance node = nodeInstanceService.rejectNode(id, dto);
        Long workflowInstanceId = node.getWorkflowInstance().getId();
        txHelper.failInstance(workflowInstanceId,
                "Rejected at APPROVAL by " + dto.getActor(),
                dto.getComment());

        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(node));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("@access.canAccessNodeInstance(#id)")
    @Operation(summary = "Retry failed node")
    public ResponseEntity<NodeInstanceResponseDTO> retry(@PathVariable Long id) {
        NodeInstance node = nodeInstanceService.retryNode(id);
        return ResponseEntity.ok(nodeInstanceMapper.toResponseDTO(node));
    }

    /** Only the assigned approver may decide; if nobody is assigned, an admin or the workflow owner may. */
    private void validateApprover(NodeInstance nodeInstance) {
        if (!accessGuard.canDecide(nodeInstance)) {
            String who = nodeInstance.getAssignedTo() != null
                    ? "the assigned approver (" + nodeInstance.getAssignedTo().getEmail() + ")"
                    : "an admin or the workflow owner";
            throw new AccessDeniedException("Only " + who + " can approve/reject this step");
        }
    }
}
