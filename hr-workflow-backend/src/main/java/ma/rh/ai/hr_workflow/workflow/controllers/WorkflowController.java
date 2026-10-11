package ma.rh.ai.hr_workflow.workflow.controllers;

import java.util.List;

import ma.rh.ai.hr_workflow.workflow.DTOs.PatchWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.config.SecurityUtils;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.service.IWorkflowService;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {
    private final IWorkflowService workflowService;

    @PostMapping
    @Operation(summary = "Create a new workflow")
    public ResponseEntity<WorkflowResponseDTO> createWorkflow(@Valid @RequestBody CreateWorkflowDTO dto) {
        WorkflowResponseDTO response = workflowService.createWorkflow(dto, SecurityUtils.currentUserEmail());
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.canAccessWorkflow(#id)")
    @Operation(summary = "Get workflow by ID")
    public ResponseEntity<WorkflowResponseDTO> getWorkflowId(
            @PathVariable @Parameter(description = "workflowId") Long id) {
        WorkflowResponseDTO responseDTO = workflowService.getWorkflowById(id);
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping
    @Operation(summary = "Get workflows — ADMIN sees all, other users see only their own.")
    public ResponseEntity<List<WorkflowResponseDTO>> getWorkflows() {
        List<WorkflowResponseDTO> workflows = SecurityUtils.isAdmin()
                ? workflowService.getAllWorkflows()
                : workflowService.getWorkflowsByCreatorEmail(SecurityUtils.currentUserEmail());
        return ResponseEntity.ok(workflows);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@access.canAccessWorkflow(#id)")
    @Operation(summary = "Update workflow")
    public ResponseEntity<WorkflowResponseDTO> updateWorkflow(
            @PathVariable Long id,
            @RequestBody UpdateWorkflowDTO dto) {
        WorkflowResponseDTO updated = workflowService.updateWorkflow(id, dto);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@access.canAccessWorkflow(#id)")
    @Operation(summary = "Patch workflow name/description (DRAFT only)")
    public ResponseEntity<WorkflowResponseDTO> patchWorkflow(
            @PathVariable Long id,
            @Valid @RequestBody PatchWorkflowDTO dto) {
        WorkflowResponseDTO updated = workflowService.patchWorkflow(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.canAccessWorkflow(#id)")
    @Operation(summary = "Delete a workflow")
    public ResponseEntity<Void> deleteWorkflow(
            @PathVariable @Parameter(description = "Workflow ID") Long id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("@access.canAccessWorkflow(#id)")
    @Operation(summary = "Duplicate a workflow with all its nodes")
    public ResponseEntity<WorkflowResponseDTO> duplicateWorkflow(@PathVariable Long id) {
        WorkflowResponseDTO copy = workflowService.duplicateWorkflow(id, SecurityUtils.currentUserEmail());
        return new ResponseEntity<>(copy, HttpStatus.CREATED);
    }
}