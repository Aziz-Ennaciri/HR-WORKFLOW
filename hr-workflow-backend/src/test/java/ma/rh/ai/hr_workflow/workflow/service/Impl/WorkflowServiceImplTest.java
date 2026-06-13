package ma.rh.ai.hr_workflow.workflow.service.Impl;

import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.PatchWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import ma.rh.ai.hr_workflow.workflow.mappers.WorkflowMapper;
import ma.rh.ai.hr_workflow.workflow.mappers.WorkflowWithNodesMapper;
import ma.rh.ai.hr_workflow.workflow.model.Node;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowServiceImpl")
class WorkflowServiceImplTest {

    @Mock private WorkflowRepository workflowRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private UserRepository userRepository;
    @Mock private WorkflowMapper workflowMapper;
    @Mock private WorkflowWithNodesMapper workflowWithNodesMapper;
    @Mock private WorkflowInstancerepository workflowInstanceRepository;

    @InjectMocks
    private WorkflowServiceImpl service;

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    private Workflow buildWorkflow(Long id, String name, WorkflowStatus status) {
        Workflow w = new Workflow();
        w.setId(id);
        w.setName(name);
        w.setStatus(status);
        w.setCreatedBy(buildUser(1L, "creator@test.com"));
        w.setDeleted(false);
        w.setNodes(new ArrayList<>());
        return w;
    }

    private WorkflowResponseDTO buildResponseDTO(Long id, String name) {
        WorkflowResponseDTO dto = new WorkflowResponseDTO();
        dto.setId(id);
        dto.setName(name);
        return dto;
    }

    // ─── createWorkflow ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createWorkflow()")
    class CreateWorkflow {

        @Test
        @DisplayName("happy path — persists workflow and returns mapped DTO")
        void createWorkflow_happyPath() {
            // Arrange
            CreateWorkflowDTO dto = new CreateWorkflowDTO("My Workflow", "desc");
            User creator = buildUser(1L, "hr@test.com");
            Workflow entity = buildWorkflow(null, "My Workflow", WorkflowStatus.DRAFT);
            Workflow saved  = buildWorkflow(10L,  "My Workflow", WorkflowStatus.DRAFT);
            WorkflowResponseDTO expected = buildResponseDTO(10L, "My Workflow");

            when(userRepository.findById(1L)).thenReturn(Optional.of(creator));
            when(workflowMapper.toEntity(dto, creator)).thenReturn(entity);
            when(workflowRepository.save(entity)).thenReturn(saved);
            when(workflowMapper.toResponseDTO(saved)).thenReturn(expected);

            // Act
            WorkflowResponseDTO result = service.createWorkflow(dto, 1L);

            // Assert
            assertThat(result).isEqualTo(expected);
            verify(workflowRepository).save(entity);
        }

        @Test
        @DisplayName("exception — creator not found throws RuntimeException")
        void createWorkflow_userNotFound_throws() {
            // Arrange
            CreateWorkflowDTO dto = new CreateWorkflowDTO("W", "d");
            when(userRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.createWorkflow(dto, 99L));
            assertThat(ex.getMessage()).contains("User not found");
            verify(workflowRepository, never()).save(any());
        }
    }

    // ─── getWorkflowById ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWorkflowById()")
    class GetWorkflowById {

        @Test
        @DisplayName("happy path — returns mapped DTO")
        void getWorkflowById_happyPath() {
            // Arrange
            Workflow w = buildWorkflow(5L, "Test", WorkflowStatus.ACTIVE);
            WorkflowResponseDTO expected = buildResponseDTO(5L, "Test");
            when(workflowRepository.findById(5L)).thenReturn(Optional.of(w));
            when(workflowMapper.toResponseDTO(w)).thenReturn(expected);

            // Act
            WorkflowResponseDTO result = service.getWorkflowById(5L);

            // Assert
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("exception — id not found throws RuntimeException")
        void getWorkflowById_notFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.getWorkflowById(99L));
            assertThat(ex.getMessage()).contains("Workflow not found");
        }
    }

    // ─── getWorkflowWithNodes ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWorkflowWithNodes()")
    class GetWorkflowWithNodes {

        @Test
        @DisplayName("happy path — delegates to mapper with workflow and its nodes")
        void getWorkflowWithNodes_happyPath() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.DRAFT);
            Node n = new Node();
            n.setId(100L);
            w.setNodes(List.of(n));
            WorkflowWithNodesResponseDTO expected = new WorkflowWithNodesResponseDTO();

            when(workflowRepository.findWorkflowWithNodes(1L)).thenReturn(w);
            when(workflowWithNodesMapper.toDTO(w, w.getNodes())).thenReturn(expected);

            // Act
            WorkflowWithNodesResponseDTO result = service.getWorkflowWithNodes(1L);

            // Assert
            assertThat(result).isEqualTo(expected);
            verify(workflowWithNodesMapper).toDTO(w, w.getNodes());
        }

        @Test
        @DisplayName("exception — null from repo throws RuntimeException with id in message")
        void getWorkflowWithNodes_nullResult_throws() {
            // Arrange
            when(workflowRepository.findWorkflowWithNodes(99L)).thenReturn(null);

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.getWorkflowWithNodes(99L));
            assertThat(ex.getMessage()).contains("99");
        }
    }

    // ─── getAllWorkflows ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllWorkflows()")
    class GetAllWorkflows {

        @Test
        @DisplayName("happy path — returns all non-deleted workflows as DTOs")
        void getAllWorkflows_happyPath() {
            // Arrange
            Workflow w1 = buildWorkflow(1L, "A", WorkflowStatus.ACTIVE);
            Workflow w2 = buildWorkflow(2L, "B", WorkflowStatus.DRAFT);
            WorkflowResponseDTO r1 = buildResponseDTO(1L, "A");
            WorkflowResponseDTO r2 = buildResponseDTO(2L, "B");

            when(workflowRepository.findByDeletedFalse()).thenReturn(List.of(w1, w2));
            when(workflowMapper.toResponseDTO(w1)).thenReturn(r1);
            when(workflowMapper.toResponseDTO(w2)).thenReturn(r2);

            // Act
            List<WorkflowResponseDTO> result = service.getAllWorkflows();

            // Assert
            assertThat(result).containsExactly(r1, r2);
        }

        @Test
        @DisplayName("edge case — empty repository returns empty list")
        void getAllWorkflows_empty() {
            // Arrange
            when(workflowRepository.findByDeletedFalse()).thenReturn(List.of());

            // Act
            List<WorkflowResponseDTO> result = service.getAllWorkflows();

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ─── getAllWorkflowsPageable ───────────────────────────────────────────────────

    @Nested
    @DisplayName("getAllWorkflowsPageable()")
    class GetAllWorkflowsPageable {

        @Test
        @DisplayName("happy path — page content is mapped to DTOs")
        void getAllWorkflowsPageable_happyPath() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.ACTIVE);
            WorkflowResponseDTO dto = buildResponseDTO(1L, "W");
            Page<Workflow> page = new PageImpl<>(List.of(w), pageable, 1);

            when(workflowRepository.findByDeletedFalse(pageable)).thenReturn(page);
            when(workflowMapper.toResponseDTO(w)).thenReturn(dto);

            // Act
            Page<WorkflowResponseDTO> result = service.getAllWorkflowsPageable(pageable);

            // Assert
            assertThat(result.getContent()).containsExactly(dto);
            assertThat(result.getTotalElements()).isEqualTo(1);
        }

        @Test
        @DisplayName("edge case — empty page returns page with no content")
        void getAllWorkflowsPageable_emptyPage() {
            // Arrange
            Pageable pageable = PageRequest.of(0, 10);
            Page<Workflow> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(workflowRepository.findByDeletedFalse(pageable)).thenReturn(emptyPage);

            // Act
            Page<WorkflowResponseDTO> result = service.getAllWorkflowsPageable(pageable);

            // Assert
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ─── getWorkflowsByCreator ────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWorkflowsByCreator()")
    class GetWorkflowsByCreator {

        @Test
        @DisplayName("happy path — returns list filtered by creator id")
        void getWorkflowsByCreator_happyPath() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.ACTIVE);
            WorkflowResponseDTO dto = buildResponseDTO(1L, "W");

            when(workflowRepository.findByCreatedByIdAndDeletedFalse(5L)).thenReturn(List.of(w));
            when(workflowMapper.toResponseDTO(w)).thenReturn(dto);

            // Act
            List<WorkflowResponseDTO> result = service.getWorkflowsByCreator(5L);

            // Assert
            assertThat(result).containsExactly(dto);
        }

        @Test
        @DisplayName("edge case — no workflows for creator id returns empty list")
        void getWorkflowsByCreator_noWorkflows_returnsEmpty() {
            // Arrange
            when(workflowRepository.findByCreatedByIdAndDeletedFalse(5L)).thenReturn(List.of());

            // Act
            List<WorkflowResponseDTO> result = service.getWorkflowsByCreator(5L);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ─── getWorkflowsByCreatorEmail ───────────────────────────────────────────────

    @Nested
    @DisplayName("getWorkflowsByCreatorEmail()")
    class GetWorkflowsByCreatorEmail {

        @Test
        @DisplayName("happy path — returns list filtered by creator email")
        void getWorkflowsByCreatorEmail_happyPath() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.ACTIVE);
            WorkflowResponseDTO dto = buildResponseDTO(1L, "W");

            when(workflowRepository.findByCreatedByEmailAndDeletedFalse("hr@test.com"))
                    .thenReturn(List.of(w));
            when(workflowMapper.toResponseDTO(w)).thenReturn(dto);

            // Act
            List<WorkflowResponseDTO> result = service.getWorkflowsByCreatorEmail("hr@test.com");

            // Assert
            assertThat(result).containsExactly(dto);
        }

        @Test
        @DisplayName("edge case — no workflows for given email returns empty list")
        void getWorkflowsByCreatorEmail_noWorkflows_returnsEmpty() {
            // Arrange
            when(workflowRepository.findByCreatedByEmailAndDeletedFalse("nobody@test.com"))
                    .thenReturn(List.of());

            // Act
            List<WorkflowResponseDTO> result = service.getWorkflowsByCreatorEmail("nobody@test.com");

            // Assert
            assertThat(result).isEmpty();
        }
    }

    // ─── updateWorkflow ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateWorkflow()")
    class UpdateWorkflow {

        @Test
        @DisplayName("happy path — optional fields null: only name/desc/key applied")
        void updateWorkflow_happyPath_nullOptionals() {
            // Arrange
            Workflow existing = buildWorkflow(1L, "Old Name", WorkflowStatus.DRAFT);
            existing.setVersion(1);
            UpdateWorkflowDTO dto = new UpdateWorkflowDTO("New Name", "new desc",
                    null, null, null, null);
            Workflow saved = buildWorkflow(1L, "New Name", WorkflowStatus.DRAFT);
            WorkflowResponseDTO expected = buildResponseDTO(1L, "New Name");

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(workflowRepository.save(existing)).thenReturn(saved);
            when(workflowMapper.toResponseDTO(saved)).thenReturn(expected);

            // Act
            WorkflowResponseDTO result = service.updateWorkflow(1L, dto);

            // Assert
            assertThat(result).isEqualTo(expected);
            assertThat(existing.getName()).isEqualTo("New Name");
            assertThat(existing.getDescription()).isEqualTo("new desc");
            assertThat(existing.getVersion()).isEqualTo(1); // unchanged
        }

        @Test
        @DisplayName("happy path — version, status and edgesJson applied when non-null")
        void updateWorkflow_happyPath_allOptionalFields() {
            // Arrange
            Workflow existing = buildWorkflow(1L, "Old", WorkflowStatus.DRAFT);
            UpdateWorkflowDTO dto = new UpdateWorkflowDTO("New", "desc",
                    3, WorkflowStatus.ACTIVE, null, "{\"edges\":[]}");
            Workflow saved = buildWorkflow(1L, "New", WorkflowStatus.ACTIVE);
            WorkflowResponseDTO expected = buildResponseDTO(1L, "New");

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(workflowRepository.save(existing)).thenReturn(saved);
            when(workflowMapper.toResponseDTO(saved)).thenReturn(expected);

            // Act
            service.updateWorkflow(1L, dto);

            // Assert — optional fields applied to the entity before save
            assertThat(existing.getVersion()).isEqualTo(3);
            assertThat(existing.getStatus()).isEqualTo(WorkflowStatus.ACTIVE);
            assertThat(existing.getEdgesJson()).isEqualTo("{\"edges\":[]}");
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void updateWorkflow_notFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());
            UpdateWorkflowDTO dto = new UpdateWorkflowDTO("N", "d", null, null, null, null);

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.updateWorkflow(99L, dto));
            assertThat(ex.getMessage()).contains("Workflow not found");
            verify(workflowRepository, never()).save(any());
        }
    }

    // ─── activateWorkflow ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("activateWorkflow()")
    class ActivateWorkflow {

        @Test
        @DisplayName("happy path — DRAFT workflow with nodes transitions to ACTIVE")
        void activateWorkflow_happyPath() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.DRAFT);
            Node n = new Node();
            n.setId(1L);
            w.setNodes(List.of(n));
            WorkflowResponseDTO expected = buildResponseDTO(1L, "W");

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));
            when(workflowMapper.toResponseDTO(w)).thenReturn(expected);

            // Act
            WorkflowResponseDTO result = service.activateWorkflow(1L);

            // Assert
            assertThat(result).isEqualTo(expected);
            assertThat(w.getStatus()).isEqualTo(WorkflowStatus.ACTIVE);
        }

        @Test
        @DisplayName("exception — non-DRAFT workflow throws IllegalStateException")
        void activateWorkflow_notDraft_throws() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.ACTIVE);
            w.setNodes(List.of(new Node()));
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.activateWorkflow(1L));
            assertThat(ex.getMessage()).contains("Only DRAFT workflows can be activated");
        }

        @Test
        @DisplayName("exception — DRAFT workflow with no nodes throws IllegalStateException")
        void activateWorkflow_noNodes_throws() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.DRAFT);
            w.setNodes(new ArrayList<>());
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.activateWorkflow(1L));
            assertThat(ex.getMessage()).contains("Workflow must have at least one node");
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void activateWorkflow_notFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.activateWorkflow(99L));
        }
    }

    // ─── deleteWorkflow ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("deleteWorkflow()")
    class DeleteWorkflow {

        @Test
        @DisplayName("happy path — cancels RUNNING and PENDING instances, soft-deletes workflow")
        void deleteWorkflow_cancelsRunningInstances() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.ACTIVE);
            WorkflowInstance running = new WorkflowInstance();
            running.setStatus(WorkflowInstanceStatus.RUNNING);
            WorkflowInstance pending = new WorkflowInstance();
            pending.setStatus(WorkflowInstanceStatus.PENDING);

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));
            when(workflowInstanceRepository.findByWorkflowIdAndStatusIn(eq(1L), anyList()))
                    .thenReturn(List.of(running, pending));

            // Act
            service.deleteWorkflow(1L);

            // Assert
            assertThat(running.getStatus()).isEqualTo(WorkflowInstanceStatus.CANCELLED);
            assertThat(pending.getStatus()).isEqualTo(WorkflowInstanceStatus.CANCELLED);
            assertThat(w.isDeleted()).isTrue();
            verify(workflowRepository).save(w);
            verify(workflowInstanceRepository, times(2)).save(any(WorkflowInstance.class));
        }

        @Test
        @DisplayName("happy path — no active instances, workflow still soft-deleted")
        void deleteWorkflow_noActiveInstances_stillDeleted() {
            // Arrange
            Workflow w = buildWorkflow(2L, "W", WorkflowStatus.DRAFT);
            when(workflowRepository.findById(2L)).thenReturn(Optional.of(w));
            when(workflowInstanceRepository.findByWorkflowIdAndStatusIn(eq(2L), anyList()))
                    .thenReturn(List.of());

            // Act
            service.deleteWorkflow(2L);

            // Assert
            assertThat(w.isDeleted()).isTrue();
            verify(workflowRepository).save(w);
            verify(workflowInstanceRepository, never()).save(any(WorkflowInstance.class));
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void deleteWorkflow_notFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.deleteWorkflow(99L));
            verify(workflowRepository, never()).save(any());
        }
    }

    // ─── duplicateWorkflow ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("duplicateWorkflow()")
    class DuplicateWorkflow {

        @Test
        @DisplayName("happy path — creates copy with 'Copy of' prefix, copies all nodes")
        void duplicateWorkflow_happyPath() {
            // Arrange
            Workflow original = buildWorkflow(1L, "Original", WorkflowStatus.ACTIVE);
            original.setEdgesJson("[{}]");
            original.setDescription("original desc");

            Node node1 = new Node();
            node1.setId(10L);
            node1.setOrderIndex(1);
            node1.setConfigJson("{\"type\":\"email\"}");

            User user = buildUser(2L, "user@test.com");

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(original));
            when(userRepository.findByEmail("user@test.com")).thenReturn(Optional.of(user));

            Workflow savedCopy = new Workflow();
            savedCopy.setId(99L);
            savedCopy.setName("Copy of Original");
            when(workflowRepository.save(any(Workflow.class))).thenReturn(savedCopy);
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(1L)).thenReturn(List.of(node1));

            WorkflowResponseDTO expected = buildResponseDTO(99L, "Copy of Original");
            when(workflowMapper.toResponseDTO(savedCopy)).thenReturn(expected);

            // Act
            WorkflowResponseDTO result = service.duplicateWorkflow(1L, "user@test.com");

            // Assert
            assertThat(result).isEqualTo(expected);

            ArgumentCaptor<Workflow> workflowCaptor = ArgumentCaptor.forClass(Workflow.class);
            verify(workflowRepository).save(workflowCaptor.capture());
            Workflow copy = workflowCaptor.getValue();
            assertThat(copy.getName()).isEqualTo("Copy of Original");
            assertThat(copy.getStatus()).isEqualTo(WorkflowStatus.DRAFT);
            assertThat(copy.getVersion()).isEqualTo(1);
            assertThat(copy.getEdgesJson()).isEqualTo("[{}]");

            ArgumentCaptor<Node> nodeCaptor = ArgumentCaptor.forClass(Node.class);
            verify(nodeRepository).save(nodeCaptor.capture());
            Node copiedNode = nodeCaptor.getValue();
            assertThat(copiedNode.getOrderIndex()).isEqualTo(1);
            assertThat(copiedNode.getConfigJson()).isEqualTo("{\"type\":\"email\"}");
            assertThat(copiedNode.getWorkflow()).isEqualTo(savedCopy);
        }

        @Test
        @DisplayName("exception — original workflow not found throws RuntimeException")
        void duplicateWorkflow_workflowNotFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.duplicateWorkflow(99L, "user@test.com"));
            assertThat(ex.getMessage()).contains("Workflow not found");
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("exception — user email not found throws RuntimeException")
        void duplicateWorkflow_userNotFound_throws() {
            // Arrange
            Workflow original = buildWorkflow(1L, "Orig", WorkflowStatus.ACTIVE);
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(original));
            when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.duplicateWorkflow(1L, "ghost@test.com"));
            assertThat(ex.getMessage()).contains("User not found");
            verify(workflowRepository, never()).save(any());
        }
    }

    // ─── archiveWorkflow ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("archiveWorkflow()")
    class ArchiveWorkflow {

        @Test
        @DisplayName("happy path — DRAFT workflow transitions to ARCHIVED")
        void archiveWorkflow_fromDraft_happyPath() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.DRAFT);
            WorkflowResponseDTO expected = buildResponseDTO(1L, "W");
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));
            when(workflowMapper.toResponseDTO(w)).thenReturn(expected);

            // Act
            WorkflowResponseDTO result = service.archiveWorkflow(1L);

            // Assert
            assertThat(result).isEqualTo(expected);
            assertThat(w.getStatus()).isEqualTo(WorkflowStatus.ARCHIVED);
        }

        @Test
        @DisplayName("happy path — UNACTIVATE workflow transitions to ARCHIVED")
        void archiveWorkflow_fromUnactivate_happyPath() {
            // Arrange
            Workflow w = buildWorkflow(2L, "W", WorkflowStatus.UNACTIVATE);
            WorkflowResponseDTO expected = buildResponseDTO(2L, "W");
            when(workflowRepository.findById(2L)).thenReturn(Optional.of(w));
            when(workflowMapper.toResponseDTO(w)).thenReturn(expected);

            // Act
            WorkflowResponseDTO result = service.archiveWorkflow(2L);

            // Assert
            assertThat(w.getStatus()).isEqualTo(WorkflowStatus.ARCHIVED);
            assertThat(result).isEqualTo(expected);
        }

        @Test
        @DisplayName("exception — ACTIVE workflow throws IllegalStateException")
        void archiveWorkflow_activeWorkflow_throws() {
            // Arrange
            Workflow w = buildWorkflow(1L, "W", WorkflowStatus.ACTIVE);
            when(workflowRepository.findById(1L)).thenReturn(Optional.of(w));

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.archiveWorkflow(1L));
            assertThat(ex.getMessage()).contains("still activated");
            assertThat(w.getStatus()).isEqualTo(WorkflowStatus.ACTIVE); // unchanged
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void archiveWorkflow_notFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.archiveWorkflow(99L));
        }
    }

    // ─── patchWorkflow ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("patchWorkflow()")
    class PatchWorkflow {

        @Test
        @DisplayName("happy path — DRAFT workflow name and description updated")
        void patchWorkflow_draft_withDescription_happyPath() {
            // Arrange
            Workflow existing = buildWorkflow(1L, "Old Name", WorkflowStatus.DRAFT);
            existing.setDescription("old desc");
            PatchWorkflowDTO dto = new PatchWorkflowDTO("New Name", "new desc");
            Workflow saved = buildWorkflow(1L, "New Name", WorkflowStatus.DRAFT);
            WorkflowResponseDTO expected = buildResponseDTO(1L, "New Name");

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(workflowRepository.save(existing)).thenReturn(saved);
            when(workflowMapper.toResponseDTO(saved)).thenReturn(expected);

            // Act
            WorkflowResponseDTO result = service.patchWorkflow(1L, dto);

            // Assert
            assertThat(result).isEqualTo(expected);
            assertThat(existing.getName()).isEqualTo("New Name");
            assertThat(existing.getDescription()).isEqualTo("new desc");
            verify(workflowRepository).save(existing);
        }

        @Test
        @DisplayName("edge case — null description leaves existing description unchanged")
        void patchWorkflow_draft_nullDescription_descriptionUnchanged() {
            // Arrange
            Workflow existing = buildWorkflow(1L, "Old Name", WorkflowStatus.DRAFT);
            existing.setDescription("keep this");
            PatchWorkflowDTO dto = new PatchWorkflowDTO("New Name", null);
            Workflow saved = buildWorkflow(1L, "New Name", WorkflowStatus.DRAFT);
            WorkflowResponseDTO expected = buildResponseDTO(1L, "New Name");

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(existing));
            when(workflowRepository.save(existing)).thenReturn(saved);
            when(workflowMapper.toResponseDTO(saved)).thenReturn(expected);

            // Act
            service.patchWorkflow(1L, dto);

            // Assert — description NOT overwritten
            assertThat(existing.getDescription()).isEqualTo("keep this");
        }

        @Test
        @DisplayName("exception — non-DRAFT workflow throws IllegalStateException")
        void patchWorkflow_notDraft_throws() {
            // Arrange
            Workflow existing = buildWorkflow(1L, "W", WorkflowStatus.ACTIVE);
            PatchWorkflowDTO dto = new PatchWorkflowDTO("New Name", "desc");

            when(workflowRepository.findById(1L)).thenReturn(Optional.of(existing));

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.patchWorkflow(1L, dto));
            assertThat(ex.getMessage()).contains("Only DRAFT workflows can be renamed or described");
            verify(workflowRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void patchWorkflow_notFound_throws() {
            // Arrange
            when(workflowRepository.findById(99L)).thenReturn(Optional.empty());
            PatchWorkflowDTO dto = new PatchWorkflowDTO("N", "d");

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.patchWorkflow(99L, dto));
            assertThat(ex.getMessage()).contains("Workflow not found");
            verify(workflowRepository, never()).save(any());
        }
    }
}
