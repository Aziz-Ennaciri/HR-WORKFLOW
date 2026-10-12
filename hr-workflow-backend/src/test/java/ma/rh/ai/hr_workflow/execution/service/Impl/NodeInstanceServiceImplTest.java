package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.execution.DTOs.ApproveNodeDTO;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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


    private NodeInstance nodeWithStatus(NodeInstanceStatus status) {
        NodeInstance ni = new NodeInstance();
        ni.setStatus(status);
        return ni;
    }

    private ApproveNodeDTO dto(String actor, String comment) {
        return new ApproveNodeDTO(actor, comment);
    }


    @Nested
    @DisplayName("startNode()")
    class StartNode {

        @Test
        @DisplayName("happy path — calls start() on nodeInstance, saves, and returns saved entity")
        void startNode_callsStartAndSaves() {
            NodeInstance ni = new NodeInstance();
            NodeInstance saved = new NodeInstance();
            saved.setId(1L);
            when(nodeInstanceRepository.save(ni)).thenReturn(saved);

            NodeInstance result = service.startNode(ni);

            assertThat(ni.getStatus()).isEqualTo(NodeInstanceStatus.IN_PROGRESS);
            assertThat(ni.getStartedAt()).isNotNull();
            assertThat(result).isEqualTo(saved);
            verify(nodeInstanceRepository).save(ni);
        }

        @Test
        @DisplayName("happy path — startedAt timestamp is set by start()")
        void startNode_setsStartedAt() {
            NodeInstance ni = new NodeInstance();
            when(nodeInstanceRepository.save(any())).thenReturn(ni);

            service.startNode(ni);

            assertThat(ni.getStartedAt()).isNotNull();
        }
    }


    @Nested
    @DisplayName("approveNode()")
    class ApproveNode {

        @Test
        @DisplayName("happy path — WAITING_APPROVAL node is approved, actor/comment set, saved, returned")
        void approveNode_waitingApprovalNode_approvesAndSaves() {
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.WAITING_APPROVAL);
            NodeInstance saved = new NodeInstance();
            saved.setId(2L);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(saved);

            NodeInstance result = service.approveNode(1L, dto("admin", "LGTM"));

            assertThat(ni.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
            assertThat(ni.getActor()).isEqualTo("admin");
            assertThat(ni.getComment()).isEqualTo("LGTM");
            assertThat(ni.getFinishedAt()).isNotNull();
            assertThat(result).isEqualTo(saved);
            verify(nodeInstanceRepository).save(ni);
        }

        @Test
        @DisplayName("happy path — markCompleted(null) copies inputData to outputData")
        void approveNode_markCompletedNull_outputDataIsInputData() {
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.WAITING_APPROVAL);
            ni.setInputData("some input");
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            service.approveNode(1L, dto("admin", null));

            assertThat(ni.getOutputData()).isEqualTo("some input");
        }

        @ParameterizedTest(name = "{0} node cannot be approved")
        @EnumSource(value = NodeInstanceStatus.class, names = "WAITING_APPROVAL", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("exception — only WAITING_APPROVAL nodes can be approved (no skipping GPT/EMAIL steps)")
        void approveNode_notWaiting_throws(NodeInstanceStatus status) {
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(nodeWithStatus(status)));

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.approveNode(1L, dto("admin", "late")));
            assertThat(ex.getMessage()).contains("waiting for approval");
            verify(nodeInstanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void approveNode_notFound_throws() {
            when(nodeInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.approveNode(99L, dto("admin", null)));
            assertThat(ex.getMessage()).contains("not found");
        }
    }


    @Nested
    @DisplayName("rejectNode()")
    class RejectNode {

        @Test
        @DisplayName("happy path — WAITING_APPROVAL node is rejected, actor/comment set, and saved")
        void rejectNode_waitingApprovalNode_marksRejectedAndSaves() {
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.WAITING_APPROVAL);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            NodeInstance result = service.rejectNode(1L, dto("admin", "not qualified"));

            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.REJECTED);
            assertThat(result.getActor()).isEqualTo("admin");
            assertThat(result.getComment()).isEqualTo("not qualified");
            assertThat(result.getFinishedAt()).isNotNull();
            verify(nodeInstanceRepository).save(ni);
        }

        @ParameterizedTest(name = "{0} node cannot be rejected")
        @EnumSource(value = NodeInstanceStatus.class, names = "WAITING_APPROVAL", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("exception — only WAITING_APPROVAL nodes can be rejected")
        void rejectNode_notWaiting_throws(NodeInstanceStatus status) {
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(nodeWithStatus(status)));

            assertThrows(IllegalStateException.class, () -> service.rejectNode(1L, dto("admin", null)));
            verify(nodeInstanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void rejectNode_notFound_throws() {
            when(nodeInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.rejectNode(99L, dto("admin", null)));
            assertThat(ex.getMessage()).contains("not found");
        }
    }


    @Nested
    @DisplayName("failNode()")
    class FailNode {

        @Test
        @DisplayName("happy path — PENDING node is failed with error message and saved")
        void failNode_pendingNode_marksFailedAndSaves() {
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.PENDING);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            NodeInstance result = service.failNode(1L, "timeout");

            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.FAILED);
            assertThat(result.getErrorMessage()).isEqualTo("timeout");
            assertThat(result.getFinishedAt()).isNotNull();
            verify(nodeInstanceRepository).save(ni);
        }

        @Test
        @DisplayName("happy path — IN_PROGRESS node is failed")
        void failNode_inProgressNode_marksFailed() {
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(2L)).thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            NodeInstance result = service.failNode(2L, "network error");

            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.FAILED);
            assertThat(result.getErrorMessage()).isEqualTo("network error");
        }

        @Test
        @DisplayName("exception — COMPLETED node cannot be failed")
        void failNode_completedNode_throws() {
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.COMPLETED);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.failNode(1L, "too late"));
            assertThat(ex.getMessage()).contains("can only fail pending and in progress");
        }

        @Test
        @DisplayName("exception — WAITING_APPROVAL node cannot be failed")
        void failNode_waitingApprovalNode_throws() {
            NodeInstance ni = nodeWithStatus(NodeInstanceStatus.WAITING_APPROVAL);
            when(nodeInstanceRepository.findById(1L)).thenReturn(Optional.of(ni));

            assertThrows(RuntimeException.class, () -> service.failNode(1L, "err"));
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void failNode_notFound_throws() {
            when(nodeInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class, () -> service.failNode(99L, "gone"));
        }
    }
}
