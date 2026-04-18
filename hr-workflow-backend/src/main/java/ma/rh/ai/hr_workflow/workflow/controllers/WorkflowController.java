package ma.rh.ai.hr_workflow.workflow.controllers;

import java.util.List;

import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;

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
    @Operation(summary = "Create a new workflow", description = "Creates a new workflow in DRAFT status")
    public ResponseEntity<WorkflowResponseDTO> createWorkflow(
            @Valid @RequestBody CreateWorkflowDTO dto,
            @RequestParam @Parameter(description = "ID of the user creating the workflow") Long creatorId) {
        WorkflowResponseDTO response = workflowService.createWorkflow(dto, creatorId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workflow by ID", description = "Retrieves a workflow by its ID")
    public ResponseEntity<WorkflowResponseDTO> getWorkflowId(@PathVariable @Parameter(description = "workflowId") Long id){
        WorkflowResponseDTO responseDTO = workflowService.getWorkflowById(id);
        return ResponseEntity.ok(responseDTO);
    }

    @GetMapping
    @Operation(summary = "Get all workflows", description = "Returns all workflows")
    public ResponseEntity<List<WorkflowResponseDTO>> getAllWorkflows() {
        List<WorkflowResponseDTO> workflows = workflowService.getAllWorkflows();
        return ResponseEntity.ok(workflows);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update workflow", description = "Updates an existing workflow")
    public ResponseEntity<WorkflowResponseDTO> updateWorkflow(
            @PathVariable Long id,
            @RequestBody UpdateWorkflowDTO dto) {
        WorkflowResponseDTO updated = workflowService.updateWorkflow(id, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a workflow", description = "Soft-deletes a workflow by marking it as deleted")
    public ResponseEntity<Void> deleteWorkflow(
            @PathVariable @Parameter(description = "Workflow ID") Long id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }
}
