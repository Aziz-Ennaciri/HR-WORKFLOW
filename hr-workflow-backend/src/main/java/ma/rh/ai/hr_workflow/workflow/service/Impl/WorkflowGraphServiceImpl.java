package ma.rh.ai.hr_workflow.workflow.service.Impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import ma.rh.ai.hr_workflow.workflow.DTOs.SaveWorkflowGraphDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.SaveWorkflowGraphDTO.GraphNode;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.graph.WorkflowGraph;
import ma.rh.ai.hr_workflow.workflow.mappers.WorkflowMapper;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import ma.rh.ai.hr_workflow.workflow.service.IWorkflowGraphService;

/**
 * Saves the editor canvas in one transaction: either every node, connection and position is
 * stored, or nothing is. Editing an ACTIVE workflow is allowed because running executions use
 * the node configs frozen when they were triggered.
 */
@Service
@RequiredArgsConstructor
public class WorkflowGraphServiceImpl implements IWorkflowGraphService {

    private final WorkflowRepository workflowRepository;
    private final NodeRepository nodeRepository;
    private final WorkflowMapper workflowMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public WorkflowResponseDTO saveGraph(Long workflowId, SaveWorkflowGraphDTO dto) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
        if (workflow.getStatus() == WorkflowStatus.ARCHIVED) {
            throw new IllegalStateException("Archived workflows cannot be edited");
        }

        List<GraphNode> graphNodes = dto.getNodes();
        List<WorkflowGraph.Edge> edges = WorkflowGraph.parseEdges(dto.getEdges());
        List<String> order = WorkflowGraph.executionOrder(graphNodes.stream().map(GraphNode::getId).toList(), edges);
        if (workflow.getStatus() == WorkflowStatus.ACTIVE && order.isEmpty()) {
            throw new IllegalStateException("An active workflow must keep at least one node");
        }

        Map<Long, Node> existing = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflowId).stream()
                .collect(Collectors.toMap(Node::getId, Function.identity()));
        Map<String, GraphNode> byClientId = graphNodes.stream()
                .collect(Collectors.toMap(GraphNode::getId, Function.identity()));

        // Upsert in execution order; remember which client id became which backend id.
        Map<String, String> idMap = new HashMap<>();
        int position = 1;
        for (String clientId : order) {
            GraphNode graphNode = byClientId.get(clientId);
            Node node = resolve(existing, clientId, workflow);
            node.setType(parseType(graphNode.getType()));
            node.setConfigJson(graphNode.getConfigJson() == null ? "{}" : graphNode.getConfigJson());
            node.setOrderIndex(position++);
            idMap.put(clientId, nodeRepository.save(node).getId().toString());
        }

        Set<Long> kept = idMap.values().stream().map(Long::valueOf).collect(Collectors.toSet());
        existing.values().stream()
                .filter(node -> !kept.contains(node.getId()))
                .forEach(nodeRepository::delete);

        workflow.setEdgesJson(WorkflowGraph.remapIds(json(dto.getEdges(), "[]"), idMap));
        workflow.setLayoutJson(dropUnknownKeys(WorkflowGraph.remapIds(json(dto.getLayout(), "{}"), idMap), idMap));
        return workflowMapper.toResponseDTO(workflowRepository.save(workflow));
    }

    private static Node resolve(Map<Long, Node> existing, String clientId, Workflow workflow) {
        Long id = parseLong(clientId);
        if (id != null && existing.containsKey(id)) {
            return existing.get(id);
        }
        if (id != null) {
            throw new IllegalArgumentException("Node " + id + " does not belong to this workflow");
        }
        Node node = new Node();
        node.setWorkflow(workflow);
        return node;
    }

    private static String json(JsonNode node, String empty) {
        return node == null || node.isNull() ? empty : node.toString();
    }

    /** Keeps only layout entries for nodes that were saved (the canvas may hold stale ids). */
    private String dropUnknownKeys(String layoutJson, Map<String, String> idMap) {
        try {
            ObjectNode layout = (ObjectNode) objectMapper.readTree(layoutJson);
            Set<String> saved = Set.copyOf(idMap.values());
            layout.retain(saved);
            return layout.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static NodeType parseType(String type) {
        try {
            return NodeType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid node type: " + type);
        }
    }

    private static Long parseLong(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
