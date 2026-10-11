package ma.rh.ai.hr_workflow.execution.controllers;

import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.config.SecurityUtils;
import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import ma.rh.ai.hr_workflow.execution.DTOs.*;
import ma.rh.ai.hr_workflow.execution.mappers.*;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowExecutionService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/executions")
@RequiredArgsConstructor
@Tag(name = "Workflow Execution")
public class WorkflowExecutionController {

    private final IWorkflowExecutionService executionService;
    private final WorkflowInstancerepository instanceRepository;
    private final UserRepository userRepository;
    private final WorkflowInstanceMapper instanceMapper;
    private final NodeInstanceMapper nodeMapper;

    @PostMapping("/trigger")
    @PreAuthorize("@access.canAccessWorkflow(#dto.workflowId)")
    @Operation(summary = "Trigger workflow execution as the current user")
    public ResponseEntity<WorkflowInstanceResponseDTO> trigger(
            @Valid @RequestBody TriggerWorkflowInstanceDTO dto) {

        User user = userRepository.findByEmail(SecurityUtils.currentUserEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        WorkflowInstance instance = executionService.triggerWorkflow(dto, user);
        
        return new ResponseEntity<>(
            instanceMapper.toResponseDTO(instance), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@access.canAccessExecution(#id)")
    @Operation(summary = "Get workflow instance")
    public ResponseEntity<WorkflowInstanceResponseDTO> getInstance(
            @PathVariable Long id) {
        
        WorkflowInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        
        return ResponseEntity.ok(instanceMapper.toResponseDTO(instance));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("@access.canAccessExecution(#id)")
    @Transactional(readOnly = true)
    @Operation(summary = "Get instance with node results")
    public ResponseEntity<?> getDetail(@PathVariable Long id) {
        return instanceRepository.findById(id)
                .map(instance -> {
                    WorkflowInstanceDetailDTO detail = new WorkflowInstanceDetailDTO();
                    detail.setWorkflowInstance(instanceMapper.toResponseDTO(instance));
                    detail.setNodeInstances(nodeMapper.toResponseDTO(instance.getNodeInstances()));
                    return ResponseEntity.ok((Object) detail);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/workflow/{workflowId}")
    @PreAuthorize("@access.canAccessWorkflow(#workflowId)")
    @Transactional(readOnly = true)
    @Operation(summary = "Get all instances for workflow")
    public ResponseEntity<List<WorkflowInstanceResponseDTO>> getByWorkflow(
            @PathVariable Long workflowId) {
        
        List<WorkflowInstance> instances = instanceRepository
                .findByWorkflowIdOrderByCreatedAtDesc(workflowId);
        
        return ResponseEntity.ok(instanceMapper.toResponseDTO(instances));
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Get workflow instances — ADMIN sees all, others see only their own workflows' executions.")
    public ResponseEntity<List<WorkflowInstanceResponseDTO>> getAllExecutions() {
        List<WorkflowInstance> instances = SecurityUtils.isAdmin()
                ? instanceRepository.findAll()
                : instanceRepository.findByWorkflow_CreatedBy_Email(SecurityUtils.currentUserEmail());
        return ResponseEntity.ok(instanceMapper.toResponseDTO(instances));
    }

    @PostMapping("/{id}/continue")
    @PreAuthorize("@access.canAccessExecution(#id)")
    @Operation(summary = "Continue/resume execution")
    public ResponseEntity<Void> continueExecution(@PathVariable Long id) {
        executionService.continueExecution(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{executionId}/retry-from/{nodeId}")
    @PreAuthorize("@access.canAccessExecution(#executionId)")
    @Operation(summary = "Retry execution starting from a failed node")
    public ResponseEntity<Void> retryFromNode(
            @PathVariable Long executionId,
            @PathVariable Long nodeId) {
        executionService.retryFromNode(executionId, nodeId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@access.canAccessExecution(#id)")
    @Transactional
    @Operation(summary = "Delete a workflow execution")
    public ResponseEntity<Void> deleteExecution(@PathVariable Long id) {
        WorkflowInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Execution not found"));
        instanceRepository.delete(instance);
        return ResponseEntity.noContent().build();
    }
}