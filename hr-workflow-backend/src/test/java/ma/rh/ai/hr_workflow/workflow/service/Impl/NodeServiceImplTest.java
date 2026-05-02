package ma.rh.ai.hr_workflow.workflow.service.Impl;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NodeServiceImpl")
class NodeServiceImplTest {

    @Mock private NodeRepository nodeRepository;
    @Mock private WorkflowRepository workflowRepository;
    @Mock private NodeMapper nodeMapper;

    @InjectMocks
    private NodeServiceImpl service;

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Workflow draftWorkflow(Long id) {
        Workflow w = new Workflow();
        w.setId(id);
        w.setStatus(WorkflowStatus.DRAFT);
        return w;
    }

    private Workflow activeWorkflow(Long id) {
        Workflow w = new Workflow();
        w.setId(id);
        w.setStatus(WorkflowStatus.ACTIVE);
        return w;
    }

    private Node buildNode(Long id, Workflow workflow, NodeType type, int order) {
        Node n = new Node();
        n.setId(id);
        n.setWorkflow(workflow);
        n.setType(type);
        n.setOrderIndex(order);
        return n;
    }

    private NodeResponseDTO buildResponseDTO(Long id, String type, int order) {
        NodeResponseDTO dto = new NodeResponseDTO();
        dto.setId(id);
        dto.setType(type);
        dto.setOrder(order);
        return dto;
    }

    // ─── createNode ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createNode()")
    class CreateNode {

        @Test
        @DisplayName("happy path — DRAFT workflow with valid type saves node and returns DTO")
        void createNode_happyPath() {
            // Arrange
            CreateNodeDTO dto = new CreateNodeDTO("GPT", 1, "{\"model\":\"llama\"}");
            Workflow w = draftWorkflow(10L);
            Node entity = buildNode(null, w, NodeType.GPT, 1);
            NodeResponseDTO expected = buildResponseDTO(1L, "GPT", 1);

            when(workflowRepository.findById(10L)).thenReturn(Optional.of(w));
            when(nodeMapper.toEntity(dto, w)).thenReturn(entity);
            when(nodeMapper.toResponseDTO(entity)).thenReturn(expected);

            // Act
            NodeResponseDTO result = service.createNode(dto, 10L);

            // Assert
            assertThat(result).isEqualTo(expected);
            verify(nodeRepository).save(entity);
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void createNode_workflowNotFound_throws() {
            // Arrange
            CreateNodeDTO dto = new CreateNodeDTO("GPT", 1, "{}");
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.createNode(dto, 99L));
            assertThat(ex.getMessage()).contains("Workflow not found");
            verify(nodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — workflow not DRAFT throws RuntimeException")
        void createNode_workflowNotDraft_throws() {
            // Arrange
            CreateNodeDTO dto = new CreateNodeDTO("GPT", 1, "{}");
            Workflow w = activeWorkflow(10L);
            when(workflowRepository.findById(10L)).thenReturn(Optional.of(w));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.createNode(dto, 10L));
            assertThat(ex.getMessage()).contains("DRAFT");
            verify(nodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — invalid node type throws IllegalArgumentException")
        void createNode_invalidType_throws() {
            // Arrange
            CreateNodeDTO dto = new CreateNodeDTO("UNKNOWN_TYPE", 1, "{}");
            Workflow w = draftWorkflow(10L);
            when(workflowRepository.findById(10L)).thenReturn(Optional.of(w));

            // Act & Assert
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.createNode(dto, 10L));
            assertThat(ex.getMessage()).contains("Invalid node type");
            verify(nodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("happy path — all valid NodeType values are accepted")
        void createNode_allValidTypes_accepted() {
            // Arrange & Act & Assert
            Workflow w = draftWorkflow(1L);
            for (NodeType type : NodeType.values()) {
                CreateNodeDTO dto = new CreateNodeDTO(type.name(), 1, "{}");
                Node entity = buildNode(null, w, type, 1);
                NodeResponseDTO response = buildResponseDTO(null, type.name(), 1);

                when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));
                when(nodeMapper.toEntity(dto, w)).thenReturn(entity);
                when(nodeMapper.toResponseDTO(entity)).thenReturn(response);

                assertDoesNotThrow(() -> service.createNode(dto, 1L));
            }
        }
    }

    // ─── createNodes ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createNodes()")
    class CreateNodes {

        @Test
        @DisplayName("happy path — saves all nodes and returns mapped DTOs")
        void createNodes_happyPath() {
            // Arrange
            CreateNodeDTO dto1 = new CreateNodeDTO("GPT", 1, "{}");
            CreateNodeDTO dto2 = new CreateNodeDTO("EMAIL", 2, "{}");
            Workflow w = draftWorkflow(10L);
            Node n1 = buildNode(null, w, NodeType.GPT, 1);
            Node n2 = buildNode(null, w, NodeType.EMAIL, 2);
            NodeResponseDTO r1 = buildResponseDTO(1L, "GPT", 1);
            NodeResponseDTO r2 = buildResponseDTO(2L, "EMAIL", 2);

            when(workflowRepository.findById(10L)).thenReturn(Optional.of(w));
            when(nodeMapper.toEntity(dto1, w)).thenReturn(n1);
            when(nodeMapper.toEntity(dto2, w)).thenReturn(n2);
            when(nodeRepository.saveAll(anyList())).thenReturn(List.of(n1, n2));
            when(nodeMapper.toResponseDTO(n1)).thenReturn(r1);
            when(nodeMapper.toResponseDTO(n2)).thenReturn(r2);

            // Act
            List<NodeResponseDTO> result = service.createNodes(List.of(dto1, dto2), 10L);

            // Assert
            assertThat(result).containsExactly(r1, r2);
            verify(nodeRepository).saveAll(argThat(list -> ((List<?>) list).size() == 2));
        }

        @Test
        @DisplayName("edge case — empty dto list saves nothing and returns empty list")
        void createNodes_emptyList_returnsEmpty() {
            // Arrange
            Workflow w = draftWorkflow(10L);
            when(workflowRepository.findById(10L)).thenReturn(Optional.of(w));
            when(nodeRepository.saveAll(anyList())).thenReturn(List.of());

            // Act
            List<NodeResponseDTO> result = service.createNodes(List.of(), 10L);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void createNodes_workflowNotFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class,
                    () -> service.createNodes(List.of(new CreateNodeDTO("GPT", 1, "{}")), 99L));
            verify(nodeRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("exception — workflow not DRAFT throws RuntimeException")
        void createNodes_workflowNotDraft_throws() {
            // Arrange
            Workflow w = activeWorkflow(10L);
            when(workflowRepository.findById(10L)).thenReturn(Optional.of(w));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.createNodes(List.of(new CreateNodeDTO("GPT", 1, "{}")), 10L));
            assertThat(ex.getMessage()).contains("DRAFT");
            verify(nodeRepository, never()).saveAll(any());
        }

        @Test
        @DisplayName("exception — one invalid type in list throws IllegalArgumentException")
        void createNodes_oneInvalidType_throws() {
            // Arrange
            Workflow w = draftWorkflow(10L);
            when(workflowRepository.findById(10L)).thenReturn(Optional.of(w));
            List<CreateNodeDTO> dtos = List.of(
                    new CreateNodeDTO("GPT", 1, "{}"),
                    new CreateNodeDTO("INVALID_TYPE", 2, "{}")
            );

            // Act & Assert
            assertThrows(IllegalArgumentException.class,
                    () -> service.createNodes(dtos, 10L));
            verify(nodeRepository, never()).saveAll(any());
        }
    }

    // ─── getNodeById ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getNodeById()")
    class GetNodeById {

        @Test
        @DisplayName("happy path — returns mapped DTO")
        void getNodeById_happyPath() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            Node n = buildNode(5L, w, NodeType.EMAIL, 2);
            NodeResponseDTO expected = buildResponseDTO(5L, "EMAIL", 2);

            when(nodeRepository.findById(5L)).thenReturn(Optional.of(n));
            when(nodeMapper.toResponseDTO(n)).thenReturn(expected);

            // Act
            NodeResponseDTO result = service.getNodeById(5L);

            // Assert
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void getNodeById_notFound_throws() {
            // Arrange
            when(nodeRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.getNodeById(99L));
            assertThat(ex.getMessage()).contains("Node not found");
        }
    }

    // ─── getNodesByWorkflowId ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getNodesByWorkflowId()")
    class GetNodesByWorkflowId {

        @Test
        @DisplayName("happy path — returns nodes ordered by orderIndex")
        void getNodesByWorkflowId_happyPath() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            Node n1 = buildNode(1L, w, NodeType.DRIVE, 1);
            Node n2 = buildNode(2L, w, NodeType.GPT, 2);
            NodeResponseDTO r1 = buildResponseDTO(1L, "DRIVE", 1);
            NodeResponseDTO r2 = buildResponseDTO(2L, "GPT", 2);

            when(workflowRepository.existsById(1L)).thenReturn(true);
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(n1, n2));
            when(nodeMapper.toResponseDTO(n1)).thenReturn(r1);
            when(nodeMapper.toResponseDTO(n2)).thenReturn(r2);

            // Act
            List<NodeResponseDTO> result = service.getNodesByWorkflowId(1L);

            // Assert
            assertThat(result).containsExactly(r1, r2);
        }

        @Test
        @DisplayName("edge case — workflow exists but no nodes returns empty list")
        void getNodesByWorkflowId_noNodes_returnsEmpty() {
            // Arrange
            when(workflowRepository.existsById(1L)).thenReturn(true);
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(1L)).thenReturn(List.of());

            // Act
            List<NodeResponseDTO> result = service.getNodesByWorkflowId(1L);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void getNodesByWorkflowId_workflowNotFound_throws() {
            // Arrange
            when(workflowRepository.existsById(99L)).thenReturn(false);

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.getNodesByWorkflowId(99L));
            assertThat(ex.getMessage()).contains("Workflow not found");
            verify(nodeRepository, never()).findByWorkflowIdOrderByOrderIndexAsc(any());
        }
    }

    // ─── getNodesByWorkflowIdAndType ──────────────────────────────────────────────

    @Nested
    @DisplayName("getNodesByWorkflowIdAndType()")
    class GetNodesByWorkflowIdAndType {

        @Test
        @DisplayName("happy path — returns nodes filtered by valid type")
        void getNodesByWorkflowIdAndType_happyPath() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            Node n = buildNode(1L, w, NodeType.GPT, 1);
            NodeResponseDTO dto = buildResponseDTO(1L, "GPT", 1);

            when(nodeRepository.findByWorkflowIdAndType(1L, NodeType.GPT)).thenReturn(List.of(n));
            when(nodeMapper.toResponseDTO(n)).thenReturn(dto);

            // Act
            List<NodeResponseDTO> result = service.getNodesByWorkflowIdAndType(1L, "GPT");

            // Assert
            assertThat(result).containsExactly(dto);
        }

        @Test
        @DisplayName("happy path — type string is case-insensitive (lowercase accepted)")
        void getNodesByWorkflowIdAndType_lowercaseType_accepted() {
            // Arrange
            when(nodeRepository.findByWorkflowIdAndType(1L, NodeType.EMAIL)).thenReturn(List.of());

            // Act & Assert — should not throw
            assertDoesNotThrow(() -> service.getNodesByWorkflowIdAndType(1L, "email"));
        }

        @Test
        @DisplayName("edge case — no matching nodes returns empty list")
        void getNodesByWorkflowIdAndType_noMatches_returnsEmpty() {
            // Arrange
            when(nodeRepository.findByWorkflowIdAndType(1L, NodeType.APPROVAL)).thenReturn(List.of());

            // Act
            List<NodeResponseDTO> result = service.getNodesByWorkflowIdAndType(1L, "APPROVAL");

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("exception — invalid type throws IllegalArgumentException")
        void getNodesByWorkflowIdAndType_invalidType_throws() {
            // Arrange & Act & Assert
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.getNodesByWorkflowIdAndType(1L, "NOT_A_TYPE"));
            assertThat(ex.getMessage()).contains("Invalid node type");
        }
    }

    // ─── updateNode ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateNode()")
    class UpdateNode {

        @Test
        @DisplayName("happy path — updates type, order and configJson, returns saved DTO")
        void updateNode_happyPath() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            Node existing = buildNode(5L, w, NodeType.GPT, 1);
            existing.setConfigJson("{\"old\":\"config\"}");
            UpdateNodeDTO dto = new UpdateNodeDTO("EMAIL", 3, "{\"new\":\"config\"}");
            Node saved = buildNode(5L, w, NodeType.EMAIL, 3);
            saved.setConfigJson("{\"new\":\"config\"}");
            NodeResponseDTO expected = buildResponseDTO(5L, "EMAIL", 3);

            when(nodeRepository.findById(5L)).thenReturn(Optional.of(existing));
            when(nodeRepository.save(existing)).thenReturn(saved);
            when(nodeMapper.toResponseDTO(saved)).thenReturn(expected);

            // Act
            NodeResponseDTO result = service.updateNode(5L, dto);

            // Assert
            assertThat(result).isEqualTo(expected);
            assertThat(existing.getType()).isEqualTo(NodeType.EMAIL);
            assertThat(existing.getOrderIndex()).isEqualTo(3);
            assertThat(existing.getConfigJson()).isEqualTo("{\"new\":\"config\"}");
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void updateNode_nodeNotFound_throws() {
            // Arrange
            when(nodeRepository.findById(99L)).thenReturn(Optional.empty());
            UpdateNodeDTO dto = new UpdateNodeDTO("GPT", 1, "{}");

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.updateNode(99L, dto));
            assertThat(ex.getMessage()).contains("Node not found");
            verify(nodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — workflow not DRAFT throws IllegalStateException")
        void updateNode_workflowNotDraft_throws() {
            // Arrange
            Workflow w = activeWorkflow(1L);
            Node existing = buildNode(5L, w, NodeType.GPT, 1);
            when(nodeRepository.findById(5L)).thenReturn(Optional.of(existing));
            UpdateNodeDTO dto = new UpdateNodeDTO("EMAIL", 2, "{}");

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.updateNode(5L, dto));
            assertThat(ex.getMessage()).contains("DRAFT");
            verify(nodeRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — invalid type in DTO throws RuntimeException")
        void updateNode_invalidType_throws() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            Node existing = buildNode(5L, w, NodeType.GPT, 1);
            when(nodeRepository.findById(5L)).thenReturn(Optional.of(existing));
            UpdateNodeDTO dto = new UpdateNodeDTO("INVALID_TYPE", 1, "{}");

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.updateNode(5L, dto));
            assertThat(ex.getMessage()).contains("Invalid node type");
            verify(nodeRepository, never()).save(any());
        }
    }

    // ─── reorderNodes ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("reorderNodes()")
    class ReorderNodes {

        @Test
        @DisplayName("happy path — updates each node's orderIndex and returns reordered list")
        void reorderNodes_happyPath() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            Node n1 = buildNode(10L, w, NodeType.GPT, 1);
            Node n2 = buildNode(20L, w, NodeType.EMAIL, 2);

            NodeOrderDTO order1 = new NodeOrderDTO(10L, 2);
            NodeOrderDTO order2 = new NodeOrderDTO(20L, 1);

            NodeResponseDTO r1 = buildResponseDTO(10L, "GPT", 2);
            NodeResponseDTO r2 = buildResponseDTO(20L, "EMAIL", 1);

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));
            when(nodeRepository.findById(10L)).thenReturn(Optional.of(n1));
            when(nodeRepository.findById(20L)).thenReturn(Optional.of(n2));
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(n2, n1));
            when(nodeMapper.toResponseDTO(n2)).thenReturn(r2);
            when(nodeMapper.toResponseDTO(n1)).thenReturn(r1);

            // Act
            List<NodeResponseDTO> result = service.reorderNodes(1L, List.of(order1, order2));

            // Assert
            assertThat(n1.getOrderIndex()).isEqualTo(2);
            assertThat(n2.getOrderIndex()).isEqualTo(1);
            assertThat(result).containsExactly(r2, r1);
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void reorderNodes_workflowNotFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class,
                    () -> service.reorderNodes(99L, List.of(new NodeOrderDTO(1L, 0))));
        }

        @Test
        @DisplayName("exception — workflow not DRAFT throws RuntimeException")
        void reorderNodes_workflowNotDraft_throws() {
            // Arrange
            Workflow w = activeWorkflow(1L);
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.reorderNodes(1L, List.of(new NodeOrderDTO(1L, 0))));
            assertThat(ex.getMessage()).contains("DRAFT");
        }

        @Test
        @DisplayName("exception — node not found in order list throws RuntimeException")
        void reorderNodes_nodeNotFound_throws() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));
            when(nodeRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.reorderNodes(1L, List.of(new NodeOrderDTO(99L, 0))));
            assertThat(ex.getMessage()).contains("Node not found with id: 99");
        }

        @Test
        @DisplayName("exception — node belongs to different workflow throws RuntimeException")
        void reorderNodes_nodeBelongsToDifferentWorkflow_throws() {
            // Arrange
            Workflow w1 = draftWorkflow(1L);
            Workflow w2 = draftWorkflow(2L);
            Node n = buildNode(10L, w2, NodeType.GPT, 1); // belongs to w2, not w1

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w1));
            when(nodeRepository.findById(10L)).thenReturn(Optional.of(n));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.reorderNodes(1L, List.of(new NodeOrderDTO(10L, 0))));
            assertThat(ex.getMessage()).contains("does not belong to workflow");
        }
    }

    // ─── deleteNode ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteNode()")
    class DeleteNode {

        @Test
        @DisplayName("happy path — DRAFT workflow, deletes node by id")
        void deleteNode_happyPath() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            Node n = buildNode(5L, w, NodeType.EMAIL, 1);
            when(nodeRepository.findById(5L)).thenReturn(Optional.of(n));

            // Act
            service.deleteNode(5L);

            // Assert
            verify(nodeRepository).deleteById(5L);
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void deleteNode_nodeNotFound_throws() {
            // Arrange
            when(nodeRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.deleteNode(99L));
            assertThat(ex.getMessage()).contains("Node not found");
            verify(nodeRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("exception — workflow not DRAFT throws RuntimeException")
        void deleteNode_workflowNotDraft_throws() {
            // Arrange
            Workflow w = activeWorkflow(1L);
            Node n = buildNode(5L, w, NodeType.EMAIL, 1);
            when(nodeRepository.findById(5L)).thenReturn(Optional.of(n));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.deleteNode(5L));
            assertThat(ex.getMessage()).contains("DRAFT");
            verify(nodeRepository, never()).deleteById(any());
        }
    }

    // ─── deleteNodesByWorkflowId ───────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteNodesByWorkflowId()")
    class DeleteNodesByWorkflowId {

        @Test
        @DisplayName("happy path — DRAFT workflow, deletes all nodes in workflow")
        void deleteNodesByWorkflowId_happyPath() {
            // Arrange
            Workflow w = draftWorkflow(1L);
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));

            // Act
            service.deleteNodesByWorkflowId(1L);

            // Assert
            verify(nodeRepository).deleteByWorkflowId(1L);
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void deleteNodesByWorkflowId_workflowNotFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.deleteNodesByWorkflowId(99L));
            assertThat(ex.getMessage()).contains("Workflow not found");
            verify(nodeRepository, never()).deleteByWorkflowId(any());
        }

        @Test
        @DisplayName("exception — workflow not DRAFT throws RuntimeException")
        void deleteNodesByWorkflowId_workflowNotDraft_throws() {
            // Arrange
            Workflow w = activeWorkflow(1L);
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.deleteNodesByWorkflowId(1L));
            assertThat(ex.getMessage()).contains("DRAFT");
            verify(nodeRepository, never()).deleteByWorkflowId(any());
        }
    }
}
