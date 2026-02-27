package ma.rh.ai.hr_workflow.workflow.service;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeOrderDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateNodeDTO;

import java.util.List;

public interface INodeService {
    NodeResponseDTO createNode(CreateNodeDTO dto, Long workflowId);

    List<NodeResponseDTO> createNodes(List<CreateNodeDTO> dtos, Long workflowId);

    NodeResponseDTO getNodeById(Long id);

    List<NodeResponseDTO> getNodesByWorkflowId(Long workflowId);

    List<NodeResponseDTO> getNodesByWorkflowIdAndType(Long workflowId, String type);

    NodeResponseDTO updateNode(Long id, UpdateNodeDTO dto);

    List<NodeResponseDTO> reorderNodes(Long workflowId, List<NodeOrderDTO> nodeOrders);

    void deleteNode(Long id);

    void deleteNodesByWorkflowId(Long workflowId);
}
