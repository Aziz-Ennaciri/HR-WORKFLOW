package ma.rh.ai.hr_workflow.workflow.service.Impl;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeOrderDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.mappers.NodeMapper;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import ma.rh.ai.hr_workflow.workflow.service.INodeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class NodeServiceImpl implements INodeService {

    private final NodeRepository nodeRepository;
    private final WorkflowRepository workflowRepository;
    private final NodeMapper nodeMapper;

    @Override
    @Transactional
    public NodeResponseDTO createNode(CreateNodeDTO createNodeDTO, Long workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId).orElseThrow(() -> new RuntimeException("Workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new RuntimeException("Nodes can only be added to DRAFT workflows");
        }
        validateNodeType(createNodeDTO.getType());
        Node node = nodeMapper.toEntity(createNodeDTO, workflow);
        nodeRepository.save(node);

        return nodeMapper.toResponseDTO(node);
    }

    @Override
    @Transactional
    public List<NodeResponseDTO> createNodes(List<CreateNodeDTO> dtos, Long workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId).orElseThrow(() -> new RuntimeException("Workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new RuntimeException("Nodes can only be added to DRAFT workflows");
        }
        List<Node> nodes = dtos.stream().map(dto -> 
            {validateNodeType(dto.getType());
                return nodeMapper.toEntity(dto, workflow);
            })
        .collect(Collectors.toList());

        return nodeRepository.saveAll(nodes).stream().map(nodeMapper::toResponseDTO).toList();
    }

    @Override
    public NodeResponseDTO getNodeById(Long id) {
        Node node = nodeRepository.findById(id).orElseThrow(() -> new RuntimeException("Node not found"));
        return nodeMapper.toResponseDTO(node);
    }

    @Override
    public List<NodeResponseDTO> getNodesByWorkflowId(Long workflowId) {
        if (!workflowRepository.existsById(workflowId)) {
            throw new RuntimeException("Workflow not found");
        }
        List<Node> nodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        return nodes.stream().map(nodeMapper::toResponseDTO).toList();
    }

    @Override
    public List<NodeResponseDTO> getNodesByWorkflowIdAndType(Long workflowId, String type) {
        
        validateNodeType(type);
        NodeType nodeType = NodeType.valueOf(type.toUpperCase());
        
        List<Node> nodes = nodeRepository.findByWorkflowIdAndType(workflowId, nodeType);
        
        return nodes.stream().map(nodeMapper::toResponseDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public NodeResponseDTO updateNode(Long id, UpdateNodeDTO dto) {
        Node node = nodeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Node not found"));

        if (!node.getWorkflow().getStatus().equals(WorkflowStatus.DRAFT)) {
            throw new IllegalStateException("Nodes can only be updated in DRAFT workflows");
        }

        try {
            NodeType nodeType = NodeType.valueOf(dto.getType());
            node.setType(nodeType);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid node type: " + dto.getType());
        }

        node.setOrderIndex(dto.getOrder());
        node.setConfigJson(dto.getConfigJson());

        Node updated = nodeRepository.save(node);
        return nodeMapper.toResponseDTO(updated);
    }

    @Override
    public List<NodeResponseDTO> reorderNodes(Long workflowId, List<NodeOrderDTO> nodeOrders) {
        Workflow workflow = workflowRepository.findById(workflowId).orElseThrow(() -> new RuntimeException("Workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new RuntimeException("Nodes can only be added to DRAFT workflows");
        }

        for (NodeOrderDTO orderDto : nodeOrders) {
            Node node = nodeRepository.findById(orderDto.getNodeId())
                    .orElseThrow(() -> new RuntimeException("Node not found with id: " + orderDto.getNodeId()));
            
            if (!node.getWorkflow().getId().equals(workflowId)) {
                throw new RuntimeException("Node " + orderDto.getNodeId() + " does not belong to workflow " + workflowId);
            }
            
            node.setOrderIndex(orderDto.getNewOrder());
        }

        List<Node> reorderedNodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId);
        
        return reorderedNodes.stream()
                .map(nodeMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteNode(Long id) {
        Node node = nodeRepository.findById(id).orElseThrow(() -> new RuntimeException("Node not found"));
        if (node.getWorkflow().getStatus() != WorkflowStatus.DRAFT) {
            throw new RuntimeException("Nodes can only be updated in DRAFT workflows");
        }
        nodeRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void deleteNodesByWorkflowId(Long workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId).orElseThrow(() -> new RuntimeException("Workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.DRAFT) {
            throw new RuntimeException("Nodes can only be added to DRAFT workflows");
        }
        nodeRepository.deleteByWorkflowId(workflowId);
    }

    /* private methodes -- Helpers */

    private void validateNodeType(String type) {
        try {
            NodeType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid node type: " + type + ". Valid types are: " + 
                String.join(", ", getValidNodeTypes()));
        }
    }
    private List<String> getValidNodeTypes() {
        return List.of(NodeType.values()).stream()
                .map(Enum::name)
                .collect(Collectors.toList());
    }
    /*  */
}
