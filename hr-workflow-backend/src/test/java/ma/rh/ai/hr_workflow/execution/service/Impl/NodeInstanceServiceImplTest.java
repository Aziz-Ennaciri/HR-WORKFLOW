package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.execution.DTOs.ApproveNodeDTO;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NodeInstanceServiceImpl")
class NodeInstanceServiceImplTest {

    @Mock
    private NodeInstanceRepository nodeInstanceRepository;

    private NodeInstanceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NodeInstanceServiceImpl(nodeInstanceRepository);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private NodeInstance nodeWithStatus(NodeInstanceStatus status) {
        NodeInstance ni = new NodeInstance();
        ni.setStatus(status);
        return ni;
    }

    private ApproveNodeDTO dto(String actor, String comment) {
        return new ApproveNodeDTO(actor, comment);
    }

    // ─── startNode ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startNode()")
    class StartNode {

        @Test
        @DisplayName("happy path — calls start() on nodeInstance, saves, and returns saved entity")
        void startNode_callsStartAndSaves() {
            // Arrange
            NodeInstance ni = new NodeInstance();
            NodeInstance saved = new NodeInstance();
            saved.setId(1L);
            when(nodeInstanceRepository.save(ni)).thenReturn(saved);

            // Act
            NodeInstance result = service.startNode(ni);

            // Assert
            assertThat(ni.getStatus()).isEqualTo(NodeInstanceStatus.IN_PROGRESS);
            assertThat(ni.getStartedAt()).isNotNull();
            assertThat(result).isEqualTo(saved);
            verify(nodeInstanceRepository).save(ni);
        }

        @Test
        @DisplayName("happy path — startedAt timestamp is set by start()")
        void startNode_setsStartedAt() {
            // Arrange
            NodeInstance ni = new NodeInstance();
            when(nodeInstanceRepository.save(any())).thenReturn(ni);

            // Act
            service.startNode(ni);

            // Assert
            assertThat(ni.getStartedAt()).isNotNull();
        }
    }

    // ─── approveNode ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("approveNode()")
    class ApproveNode {

        @Test
        @DisplayName("happy path — PENDING node is approved, actor/comment set, saved, returned")
        void approveNode_pendingNode_approvesAndSaves() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.PENDING);
            NodeInstance saved = new NodeInstance();
            saved.setId(2L);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(saved);

            // Act
            NodeInstance result = service.approveNode(1L, dto("admin", "LGTM"));

            // Assert
            assertThat(ni.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
            assertThat(ni.getActor()).isEqualTo("admin");
            assertThat(ni.getComment()).isEqualTo("LGTM");
            assertThat(ni.getFinishedAt()).isNotNull();
            assertThat(result).isEqualTo(saved);
            verify(nodeInstanceRepository).save(ni);
        }

        @Test
        @DisplayName("happy path — IN_PROGRESS node is approved")
        void approveNode_inProgressNode_approvesAndSaves() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(2L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            // Act
            service.approveNode(2L, dto("user", "ok"));

            // Assert
            assertThat(ni.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
        }

        @Test
        @DisplayName("happy path — WAITING_APPROVAL node is approved")
        void approveNode_waitingApprovalNode_approvesAndSaves() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.WAITING_APPROVAL);
            when(nodeInstanceRepository.findById(3L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            // Act
            service.approveNode(3L, dto("mgr", "approved"));

            // Assert
            assertThat(ni.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
        }

        @Test
        @DisplayName("happy path — markCompleted(null) copies inputData to outputData")
        void approveNode_markCompletedNull_outputDataIsInputData() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.PENDING);
            ni.setInputData("some input");
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            // Act
            service.approveNode(1L, dto("admin", null));

            // Assert — markCompleted(null) sets outputData = inputData
            assertThat(ni.getOutputData()).isEqualTo("some input");
        }

        @Test
        @DisplayName("exception — COMPLETED node cannot be approved")
        void approveNode_completedNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.COMPLETED);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.approveNode(1L, dto("admin", "late")));
            assertThat(ex.getMessage()).contains("can only approve");
        }

        @Test
        @DisplayName("exception — REJECTED node cannot be approved")
        void approveNode_rejectedNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.REJECTED);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.approveNode(1L, dto("x", null)));
        }

        @Test
        @DisplayName("exception — FAILED node cannot be approved")
        void approveNode_failedNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.FAILED);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.approveNode(1L, dto("x", null)));
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void approveNode_notFound_throws() {
            // Arrange
            when(nodeInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.approveNode(99L, dto("admin", null)));
            assertThat(ex.getMessage()).contains("not found");
        }
    }

    // ─── rejectNode ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rejectNode()")
    class RejectNode {

        @Test
        @DisplayName("happy path — PENDING node is rejected, actor/comment set, NOT saved")
        void rejectNode_pendingNode_marksRejectedWithoutSave() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.PENDING);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act
            NodeInstance result = service.rejectNode(1L, dto("admin", "not qualified"));

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.REJECTED);
            assertThat(result.getActor()).isEqualTo("admin");
            assertThat(result.getComment()).isEqualTo("not qualified");
            assertThat(result.getFinishedAt()).isNotNull();
            verify(nodeInstanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("happy path — IN_PROGRESS node is rejected")
        void rejectNode_inProgressNode_marksRejected() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(2L)).thenReturn(Optional.of(ni));

            // Act
            NodeInstance result = service.rejectNode(2L, dto("hr", "underqualified"));

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.REJECTED);
        }

        @Test
        @DisplayName("exception — COMPLETED node cannot be rejected")
        void rejectNode_completedNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.COMPLETED);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.rejectNode(1L, dto("admin", null)));
            assertThat(ex.getMessage()).contains("can only approve pending and in progress");
        }

        @Test
        @DisplayName("exception — WAITING_APPROVAL node cannot be rejected")
        void rejectNode_waitingApprovalNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.WAITING_APPROVAL);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.rejectNode(1L, dto("x", null)));
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void rejectNode_notFound_throws() {
            // Arrange
            when(nodeInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.rejectNode(99L, dto("admin", null)));
            assertThat(ex.getMessage()).contains("not found");
        }
    }

    // ─── failNode ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("failNode()")
    class FailNode {

        @Test
        @DisplayName("happy path — PENDING node is failed with error message, NOT saved")
        void failNode_pendingNode_marksFailedWithoutSave() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.PENDING);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act
            NodeInstance result = service.failNode(1L, "timeout");

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.FAILED);
            assertThat(result.getErrorMessage()).isEqualTo("timeout");
            assertThat(result.getFinishedAt()).isNotNull();
            verify(nodeInstanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("happy path — IN_PROGRESS node is failed")
        void failNode_inProgressNode_marksFailed() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(2L)).thenReturn(Optional.of(ni));

            // Act
            NodeInstance result = service.failNode(2L, "network error");

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.FAILED);
            assertThat(result.getErrorMessage()).isEqualTo("network error");
        }

        @Test
        @DisplayName("exception — COMPLETED node cannot be failed")
        void failNode_completedNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.COMPLETED);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.failNode(1L, "too late"));
            assertThat(ex.getMessage()).contains("can only approve pending and in progress");
        }

        @Test
        @DisplayName("exception — WAITING_APPROVAL node cannot be failed")
        void failNode_waitingApprovalNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.WAITING_APPROVAL);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.failNode(1L, "err"));
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void failNode_notFound_throws() {
            // Arrange
            when(nodeInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.failNode(99L, "gone"));
        }
    }

    // ─── retryNode ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("retryNode()")
    class RetryNode {

        @Test
        @DisplayName("happy path — FAILED node creates new RETRYING instance with incremented retryCount")
        void retryNode_failedNode_createsNewRetryInstance() {
            // Arrange
            WorkflowInstance wfInstance = new WorkflowInstance();
            Node node = new Node();
            node.setId(10L);

            NodeInstance failed = new NodeInstance();
            failed.setId(5L);
            failed.setStatus(NodeInstanceStatus.FAILED);
            failed.setWorkflowInstance(wfInstance);
            failed.setNode(node);
            failed.setExecutionOrder(2);
            failed.setInputData("original input");
            failed.setRetryCount(1);

            when(nodeInstanceRepository.findById(5L)).thenReturn(Optional.of(failed));

            ArgumentCaptor<NodeInstance> captor = ArgumentCaptor.forClass(NodeInstance.class);
            NodeInstance saved = new NodeInstance();
            saved.setId(6L);
            when(nodeInstanceRepository.save(captor.capture())).thenReturn(saved);

            // Act
            NodeInstance result = service.retryNode(5L);

            // Assert
            assertThat(result).isEqualTo(saved);
            NodeInstance created = captor.getValue();
            assertThat(created.getStatus()).isEqualTo(NodeInstanceStatus.RETRYING);
            assertThat(created.getWorkflowInstance()).isEqualTo(wfInstance);
            assertThat(created.getNode()).isEqualTo(node);
            assertThat(created.getExecutionOrder()).isEqualTo(2);
            assertThat(created.getInputData()).isEqualTo("original input");
            assertThat(created.getRetryCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("happy path — retryCount starts at 0, becomes 1 on first retry")
        void retryNode_firstRetry_retryCountIsOne() {
            // Arrange
            NodeInstance failed = new NodeInstance();
            failed.setStatus(NodeInstanceStatus.FAILED);
            failed.setRetryCount(0);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(failed));

            ArgumentCaptor<NodeInstance> captor = ArgumentCaptor.forClass(NodeInstance.class);
            when(nodeInstanceRepository.save(captor.capture())).thenReturn(new NodeInstance());

            // Act
            service.retryNode(1L);

            // Assert
            assertThat(captor.getValue().getRetryCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("exception — PENDING node cannot be retried")
        void retryNode_pendingNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.PENDING);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class, () -> service.retryNode(1L));
            assertThat(ex.getMessage()).contains("can only retry failed nodes");
        }

        @Test
        @DisplayName("exception — IN_PROGRESS node cannot be retried")
        void retryNode_inProgressNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.retryNode(1L));
        }

        @Test
        @DisplayName("exception — COMPLETED node cannot be retried")
        void retryNode_completedNode_throws() {
            // Arrange
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.COMPLETED);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.retryNode(1L));
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void retryNode_notFound_throws() {
            // Arrange
            when(nodeInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> service.retryNode(99L));
        }
    }
}
