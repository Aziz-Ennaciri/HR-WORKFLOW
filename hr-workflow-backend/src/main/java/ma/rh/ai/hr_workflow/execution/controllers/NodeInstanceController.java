package ma.rh.ai.hr_workflow.execution.controllers;

import java.util.List;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.config.AccessGuard;
import ma.rh.ai.hr_workflow.config.SecurityUtils;
import ma.rh.ai.hr_workflow.execution.DTOs.ApproveNodeDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.NodeInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.PendingApprovalDTO;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.service.IExecutionQueryService;
import ma.rh.ai.hr_workflow.execution.service.INodeInstanceService;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowExecutionService;

@RestController
@RequestMapping("/api/v1/executions/nodes")
@RequiredArgsConstructor
@Tag(name = "Node Instance")
public class NodeInstanceController {

    private final INodeInstanceService nodeInstanceService;
    private final IExecutionQueryService queryService;
    private final IWorkflowExecutionService executionService;
    private final AccessGuard accessGuard;

    @GetMapping("/{id}")
    @PreAuthorize("@access.canAccessNodeInstance(#id)")
    @Operation(summary = "Get node instance")
    public NodeInstanceResponseDTO getNode(@PathVariable Long id) {
        return queryService.getNodeInstance(id);
    }

    @GetMapping("/workflow-instance/{instanceId}")
    @PreAuthorize("@access.canAccessExecution(#instanceId)")
    @Operation(summary = "Get all nodes for instance")
    public List<NodeInstanceResponseDTO> getNodes(@PathVariable Long instanceId) {
        return queryService.getNodeInstances(instanceId);
    }

    @GetMapping("/my-approvals")
    @Operation(summary = "Get pending approvals for the current user")
    public List<PendingApprovalDTO> getMyApprovals() {
        return queryService.getMyApprovals();
    }

    @GetMapping("/my-approvals/count")
    @Operation(summary = "Count pending approvals for the current user")
    public long getMyApprovalsCount() {
        return queryService.countMyApprovals();
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve node and resume workflow")
    public NodeInstanceResponseDTO approve(@PathVariable Long id, @Valid @RequestBody ApproveNodeDTO dto) {
        validateApprover(id);
        // The actor is always the authenticated user, never a value supplied by the client
        dto.setActor(SecurityUtils.currentUserEmail());
        NodeInstance step = nodeInstanceService.approveNode(id, dto);
        // Resuming is idempotent: only one worker can claim the paused execution.
        executionService.resumeAfterApproval(step.getWorkflowInstance().getId());
        return queryService.getNodeInstance(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject node and stop workflow")
    public NodeInstanceResponseDTO reject(@PathVariable Long id, @Valid @RequestBody ApproveNodeDTO dto) {
        validateApprover(id);
        dto.setActor(SecurityUtils.currentUserEmail());
        nodeInstanceService.rejectNode(id, dto);
        return queryService.getNodeInstance(id);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("@access.canAccessNodeInstance(#id)")
    @Operation(summary = "Retry a failed step and resume the execution from it")
    public NodeInstanceResponseDTO retry(@PathVariable Long id) {
        executionService.retryStep(id);
        return queryService.getNodeInstance(id);
    }

    /** Only the assigned approver may decide; if nobody is assigned, an admin or the workflow owner may. */
    private void validateApprover(Long nodeInstanceId) {
        if (!accessGuard.canDecide(nodeInstanceId)) {
            throw new AccessDeniedException("Only the assigned approver, or the workflow owner/an admin when none is assigned, can decide this step");
        }
    }
}
