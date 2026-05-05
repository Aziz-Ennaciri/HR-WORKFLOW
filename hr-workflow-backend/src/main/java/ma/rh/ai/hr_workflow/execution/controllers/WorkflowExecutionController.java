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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<WorkflowInstance> instances;
        if (isAdmin(auth)) {
            instances = instanceRepository.findAll();
        } else {
            String email = auth.getName();
            instances = instanceRepository.findByWorkflow_CreatedBy_Email(email);
        }
        return ResponseEntity.ok(instanceMapper.toResponseDTO(instances));
    }

    private boolean isAdmin(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping("/{id}/continue")
    @Operation(summary = "Continue/resume execution")
    public ResponseEntity<Void> continueExecution(@PathVariable Long id) {
        executionService.continueExecution(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{executionId}/retry-from/{nodeId}")
    @Operation(summary = "Retry execution starting from a failed node")
    public ResponseEntity<Void> retryFromNode(
            @PathVariable Long executionId,
            @PathVariable Long nodeId) {
        executionService.retryFromNode(executionId, nodeId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Delete a workflow execution")
    public ResponseEntity<Void> deleteExecution(@PathVariable Long id) {
        WorkflowInstance instance = instanceRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Execution not found"));
        instanceRepository.delete(instance);
        return ResponseEntity.noContent().build();
    }
}