package ma.rh.ai.hr_workflow.workflow.controllers;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeOrderDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.service.INodeService;

@RestController
@RequiredArgsConstructor
public class NodeController {
    private final INodeService nodeService;

    @PostMapping("/workflow/{workflowId}")
    @Operation(summary = "Create a new node", description = "Creates a new node for a workflow (only allowed in DRAFT workflows)")
    public ResponseEntity<NodeResponseDTO> createNode(
        @Valid @RequestBody CreateNodeDTO dto,
        @PathVariable @Parameter(description = "Workflow ID") Long workflowId) {
        NodeResponseDTO response = nodeService.createNode(dto, workflowId);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get node by ID", description = "Retrieves a node by its ID")
    public ResponseEntity<NodeResponseDTO> getNodeId(@PathVariable @Parameter(description = "Node ID")Long id){
        NodeResponseDTO nodeResponseDTO = nodeService.getNodeById(id);
        return ResponseEntity.ok(nodeResponseDTO);
    }

    @GetMapping
    @Operation(summary = "Get all nodes for a workflow", description = "Retrieves all nodes for a workflow, ordered by execution order (orderIndex)")
    public ResponseEntity<List<NodeResponseDTO>> getNodesByWorkflowId(@PathVariable @Parameter(description = "Workflow ID")Long workflowId){
        List<NodeResponseDTO> responseDTOs = nodeService.getNodesByWorkflowId(workflowId);
        return ResponseEntity.ok(responseDTOs);
    }

    @GetMapping
    @Operation(summary = "Get nodes by type", description = "Retrieves nodes of a specific type for a workflow (GPT, DRIVE, EMAIL, EXCEL)")
    public ResponseEntity<List<NodeResponseDTO>> getNodesByWorkflowIdAndType(
        @PathVariable @Parameter(description = "Workflow ID")Long workflowId,
        @PathVariable @Parameter(description = "Node Type")String type){
        List<NodeResponseDTO> responseDTOs = nodeService.getNodesByWorkflowIdAndType(workflowId, type);
        return ResponseEntity.ok(responseDTOs);                                                                    
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a node", description = "Updates a node's type, order, or configuration (only allowed in DRAFT workflows)")
    public ResponseEntity<NodeResponseDTO> updateNode(
        @Valid @RequestBody UpdateNodeDTO dto,
        @PathVariable @Parameter(description = "Node ID")Long id){
        NodeResponseDTO responseDTO = nodeService.updateNode(id, dto);
        return ResponseEntity.ok(responseDTO);
    }

    @PatchMapping("/workflow/{workflowId}/reorder")
    @Operation(summary = "Reorder nodes", description = "Changes the execution order of nodes in a workflow")
    public ResponseEntity<List<NodeResponseDTO>> reorderNodes(
        @PathVariable @Parameter(description = "Workflow ID")Long workflowID,
        @Valid @RequestBody List<NodeOrderDTO> nodeOrderDTOs){
        List<NodeResponseDTO> dtos = nodeService.reorderNodes(workflowID, nodeOrderDTOs);
        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a node", description = "Deletes a node (only allowed in DRAFT workflows)")
    public ResponseEntity<NodeResponseDTO> deleteNode(@PathVariable @Parameter(description = "Node ID")Long nodeId){
        nodeService.deleteNode(nodeId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/workflow/{workflowId}/")
    @Operation(summary = "Delete all nodes for a workflow", description = "Deletes all nodes in a workflow (only allowed in DRAFT workflows)")
    public ResponseEntity<List<NodeResponseDTO>> deleteNodesByWorkflowId(@PathVariable @Parameter(description = "Workflow ID")Long workflowId){
        nodeService.deleteNodesByWorkflowId(workflowId);
        return ResponseEntity.noContent().build();
    }
}
