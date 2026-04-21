package ma.rh.ai.hr_workflow.execution.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.DTOs.*;
import ma.rh.ai.hr_workflow.execution.mappers.NodeInstanceMapper;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.execution.service.INodeInstanceService;
import ma.rh.ai.hr_workflow.execution.service.Impl.ExecutionTransactionHelper;
import ma.rh.ai.hr_workflow.execution.service.Impl.WorkflowExecutionServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve node and resume workflow")
    public ResponseEntity<NodeInstanceResponseDTO> approve(
            @PathVariable Long id,
            @Valid @RequestBody ApproveNodeDTO dto) {

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
}