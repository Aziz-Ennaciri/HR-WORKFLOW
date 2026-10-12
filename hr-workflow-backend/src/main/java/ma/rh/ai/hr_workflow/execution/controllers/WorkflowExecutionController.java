package ma.rh.ai.hr_workflow.execution.controllers;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.common.PageResponse;
import ma.rh.ai.hr_workflow.config.SecurityUtils;
import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import ma.rh.ai.hr_workflow.execution.DTOs.ExecutionStatsDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceDetailDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.service.IExecutionQueryService;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowExecutionService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@Tag(name = "Workflow Execution")
public class WorkflowExecutionController {

    private final IWorkflowExecutionService executionService;
    private final IExecutionQueryService queryService;
    private final UserRepository userRepository;

    @PostMapping("/trigger")
    @PreAuthorize("@access.canAccessWorkflow(#dto.workflowId)")
    @Operation(summary = "Trigger workflow execution as the current user")
    public ResponseEntity<WorkflowInstanceResponseDTO> trigger(@Valid @RequestBody TriggerWorkflowInstanceDTO dto) {
        User user = userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        Long executionId = executionService.triggerWorkflow(dto, user).getId();
        return new ResponseEntity<>(queryService.getExecution(executionId), HttpStatus.CREATED);
    }

    @GetMapping
    @Operation(summary = "Executions visible to the current user (admins see all), newest first, filterable")
    public PageResponse<WorkflowInstanceResponseDTO> getExecutions(
            @RequestParam(required = false) WorkflowInstanceStatus status,
            @RequestParam(required = false) Long workflowId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return queryService.searchExecutions(status, workflowId, pageable);
    }

    @GetMapping("/stats")
    @Operation(summary = "Execution counts by status and by workflow for the current user")
    public ExecutionStatsDTO getStats() {
        return queryService.getStats();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.canAccessExecution(#id)")
    @Operation(summary = "Get workflow instance")
    public WorkflowInstanceResponseDTO getInstance(@PathVariable Long id) {
        return queryService.getExecution(id);
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("@access.canAccessExecution(#id)")
    @Operation(summary = "Get instance with node results")
    public WorkflowInstanceDetailDTO getDetail(@PathVariable Long id) {
        return queryService.getExecutionDetail(id);
    }

    @GetMapping("/workflow/{workflowId}")
    @PreAuthorize("@access.canAccessWorkflow(#workflowId)")
    @Operation(summary = "Get all instances for workflow")
    public List<WorkflowInstanceResponseDTO> getByWorkflow(@PathVariable Long workflowId) {
        return queryService.getExecutionsForWorkflow(workflowId);
    }

    @PostMapping("/{id}/continue")
    @PreAuthorize("@access.canAccessExecution(#id)")
    @Operation(summary = "Queue a pending or resumable execution to run")
    public ResponseEntity<Void> continueExecution(@PathVariable Long id) {
        executionService.continueExecution(id);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{executionId}/retry-from/{nodeId}")
    @PreAuthorize("@access.canAccessExecution(#executionId)")
    @Operation(summary = "Retry execution starting from a failed node")
    public ResponseEntity<Void> retryFromNode(@PathVariable Long executionId, @PathVariable Long nodeId) {
        executionService.retryFromNode(executionId, nodeId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.canAccessExecution(#id)")
    @Operation(summary = "Delete a workflow execution")
    public ResponseEntity<Void> deleteExecution(@PathVariable Long id) {
        queryService.deleteExecution(id);
        return ResponseEntity.noContent().build();
    }
}
