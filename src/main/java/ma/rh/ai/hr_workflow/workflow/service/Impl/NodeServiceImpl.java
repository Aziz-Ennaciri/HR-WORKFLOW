package ma.rh.ai.hr_workflow.workflow.service.Impl;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeOrderDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.service.INodeService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NodeServiceImpl implements INodeService {
    @Override
    public NodeResponseDTO createNode(CreateNodeDTO dto, Long workflowId) {
        return null;
    }

    @Override
    public List<NodeResponseDTO> createNodes(List<CreateNodeDTO> dtos, Long workflowId) {
        return List.of();
    }

    @Override
    public NodeResponseDTO getNodeById(Long id) {
        return null;
    }

    @Override
    public List<NodeResponseDTO> getNodesByWorkflowId(Long workflowId) {
        return List.of();
    }

    @Override
    public List<NodeResponseDTO> getNodesByWorkflowIdAndType(Long workflowId, String type) {
        return List.of();
    }

    @Override
    public NodeResponseDTO updateNode(Long id, UpdateNodeDTO dto) {
        return null;
    }

    @Override
    public List<NodeResponseDTO> reorderNodes(Long workflowId, List<NodeOrderDTO> nodeOrders) {
        return List.of();
    }

    @Override
    public void deleteNode(Long id) {

    }

    @Override
    public void deleteNodesByWorkflowId(Long workflowId) {

    }
}
