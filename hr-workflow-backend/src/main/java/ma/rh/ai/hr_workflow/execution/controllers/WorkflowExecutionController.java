package ma.rh.ai.hr_workflow.execution.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.DTOs.*;
import ma.rh.ai.hr_workflow.execution.mappers.*;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowExecutionService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    @Operation(summary = "Trigger workflow execution")
    public ResponseEntity<WorkflowInstanceResponseDTO> trigger(
            @Valid @RequestBody TriggerWorkflowInstanceDTO dto,
            @RequestParam Long userId) {
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        WorkflowInstance instance = executionService.triggerWorkflow(dto, user);
        
        return new ResponseEntity<>(
            instanceMapper.toResponseDTO(instance), HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get workflow instance")
    public ResponseEntity<WorkflowInstanceResponseDTO> getInstance(
            @PathVariable Long id) {
        
        WorkflowInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found"));
        
        return ResponseEntity.ok(instanceMapper.toResponseDTO(instance));
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "Get instance with node results")
    public ResponseEntity<WorkflowInstanceDetailDTO> getDetail(
            @PathVariable Long id) {
        
        WorkflowInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found"));
        
        WorkflowInstanceDetailDTO detail = new WorkflowInstanceDetailDTO();
        detail.setWorkflowInstance(instanceMapper.toResponseDTO(instance));
        detail.setNodeInstances(nodeMapper.toResponseDTO(instance.getNodeInstances()));
        
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/workflow/{workflowId}")
    @Operation(summary = "Get all instances for workflow")
    public ResponseEntity<List<WorkflowInstanceResponseDTO>> getByWorkflow(
            @PathVariable Long workflowId) {
        
        List<WorkflowInstance> instances = instanceRepository
                .findByWorkflowIdOrderByCreatedAtDesc(workflowId);
        
        return ResponseEntity.ok(instanceMapper.toResponseDTO(instances));
    }

    @GetMapping
    @Operation(summary = "Get all workflow instances")
    public ResponseEntity<List<WorkflowInstanceResponseDTO>> getAllExecutions() {
        List<WorkflowInstance> instances = instanceRepository.findAll();
        return ResponseEntity.ok(instanceMapper.toResponseDTO(instances));
    }

    @PostMapping("/{id}/continue")
    @Operation(summary = "Continue/resume execution")
    public ResponseEntity<Void> continueExecution(@PathVariable Long id) {
        executionService.continueExecution(id);
        return ResponseEntity.ok().build();
    }
}