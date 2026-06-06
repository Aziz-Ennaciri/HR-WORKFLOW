package ma.rh.ai.hr_workflow.workflow.controllers;

import java.util.List;

import ma.rh.ai.hr_workflow.workflow.DTOs.PatchWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<WorkflowResponseDTO> createWorkflow(
            @Valid @RequestBody CreateWorkflowDTO dto,
            @RequestParam @Parameter(description = "ID of the user creating the workflow") Long creatorId) {
        WorkflowResponseDTO response = workflowService.createWorkflow(dto, creatorId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workflow by ID")
    public ResponseEntity<WorkflowResponseDTO> getWorkflowId(
            @PathVariable @Parameter(description = "workflowId") Long id) {
        WorkflowResponseDTO responseDTO = workflowService.getWorkflowById(id);
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping
    @Operation(summary = "Get workflows — ADMIN sees all, other users see only their own.")
    public ResponseEntity<List<WorkflowResponseDTO>> getWorkflows() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<WorkflowResponseDTO> workflows;
        if (isAdmin(auth)) {
            workflows = workflowService.getAllWorkflows();
        } else {
            String email = auth.getName();
            workflows = workflowService.getWorkflowsByCreatorEmail(email);
        }
        return ResponseEntity.ok(workflows);
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update workflow")
    public ResponseEntity<WorkflowResponseDTO> updateWorkflow(
            @PathVariable Long id,
            @RequestBody UpdateWorkflowDTO dto) {
        WorkflowResponseDTO updated = workflowService.updateWorkflow(id, dto);
        return ResponseEntity.ok(updated);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Patch workflow name/description (DRAFT only)")
    public ResponseEntity<WorkflowResponseDTO> patchWorkflow(
            @PathVariable Long id,
            @Valid @RequestBody PatchWorkflowDTO dto) {
        WorkflowResponseDTO updated = workflowService.patchWorkflow(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a workflow")
    public ResponseEntity<Void> deleteWorkflow(
            @PathVariable @Parameter(description = "Workflow ID") Long id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Duplicate a workflow with all its nodes")
    public ResponseEntity<WorkflowResponseDTO> duplicateWorkflow(@PathVariable Long id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String email = auth.getName();
        WorkflowResponseDTO copy = workflowService.duplicateWorkflow(id, email);
        return new ResponseEntity<>(copy, HttpStatus.CREATED);
    }
}