package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.execution.handler.ApprovalNodeHandler;
import ma.rh.ai.hr_workflow.execution.handler.NodeHandler;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionTransactionHelper")
class ExecutionTransactionHelperTest {

    @Mock
    private WorkflowInstancerepository workflowInstanceRepository;

    @Mock
    private NodeInstanceRepository nodeInstanceRepository;

    @Mock
    private NodeRepository nodeRepository;

    @InjectMocks
    private ExecutionTransactionHelper helper;

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private WorkflowInstance pendingInstance(Long id) {
        WorkflowInstance wi = new WorkflowInstance();
        wi.setId(id);
        wi.setStatus(WorkflowInstanceStatus.PENDING);
        Workflow wf = new Workflow();
        wf.setId(10L);
        wf.setName("Test WF");
        wf.setWorkflowKey("test-wf");
        wi.setWorkflow(wf);
        wi.setInputData("{\"key\":\"value\"}");
        return wi;
    }

    private Node makeNode(Long id, NodeType type, int orderIndex) {
        Node node = new Node();
        node.setId(id);
        node.setType(type);
        node.setOrderIndex(orderIndex);
        return node;
    }

    private NodeInstance pendingNodeInstance(Long id, WorkflowInstance wi, Node node) {
        NodeInstance ni = new NodeInstance();
        ni.setId(id);
        ni.setWorkflowInstance(wi);
        ni.setNode(node);
        ni.setStatus(NodeInstanceStatus.PENDING);
        ni.setInputData("input");
        return ni;
    }

    // ─────────────────────────────────────────────────────────────────
    // startInstance()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("startInstance()")
    class StartInstance {

        @Test
        @DisplayName("happy path — PENDING instance is started and saved")
        void startInstance_pendingInstance_startsAndSaves() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(workflowInstanceRepository.save(wi)).thenReturn(wi);

            // Act
            helper.startInstance(1L);

            // Assert
            assertThat(wi.getStatus()).isEqualTo(WorkflowInstanceStatus.RUNNING);
            verify(workflowInstanceRepository).save(wi);
        }

        @Test
        @DisplayName("edge case — already RUNNING instance is NOT saved again")
        void startInstance_alreadyRunning_notSavedAgain() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            wi.setStatus(WorkflowInstanceStatus.RUNNING);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));

            // Act
            helper.startInstance(1L);

            // Assert
            verify(workflowInstanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void startInstance_notFound_throws() {
            // Arrange
            when(workflowInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> helper.startInstance(99L));
            assertThat(ex.getMessage()).contains("Workflow instance not found");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // getWorkflowNodes()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getWorkflowNodes()")
    class GetWorkflowNodes {

        @Test
        @DisplayName("happy path — delegates to nodeRepository with correct workflow id")
        void getWorkflowNodes_happyPath() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node n1 = makeNode(1L, NodeType.EMAIL, 0);
            Node n2 = makeNode(2L, NodeType.GPT, 1);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(10L)).thenReturn(List.of(n1, n2));

            // Act
            List<Node> result = helper.getWorkflowNodes(1L);

            // Assert
            assertThat(result).containsExactly(n1, n2);
            verify(nodeRepository).findByWorkflowIdOrderByOrderIndexAsc(10L);
        }

        @Test
        @DisplayName("edge case — workflow with no nodes returns empty list")
        void getWorkflowNodes_noNodes_returnsEmpty() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(10L)).thenReturn(List.of());

            // Act
            List<Node> result = helper.getWorkflowNodes(1L);

            // Assert
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void getWorkflowNodes_instanceNotFound_throws() {
            // Arrange
            when(workflowInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> helper.getWorkflowNodes(99L));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // getInstanceInputData()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getInstanceInputData()")
    class GetInstanceInputData {

        @Test
        @DisplayName("happy path — returns inputData from the instance")
        void getInstanceInputData_happyPath() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            wi.setInputData("{\"x\":1}");
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));

            // Act
            String result = helper.getInstanceInputData(1L);

            // Assert
            assertThat(result).isEqualTo("{\"x\":1}");
        }

        @Test
        @DisplayName("edge case — null inputData is returned as-is")
        void getInstanceInputData_nullData_returnsNull() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            wi.setInputData(null);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));

            // Act
            String result = helper.getInstanceInputData(1L);

            // Assert
            assertThat(result).isNull();
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void getInstanceInputData_notFound_throws() {
            // Arrange
            when(workflowInstanceRepository.findById(5L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> helper.getInstanceInputData(5L));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // updateCurrentNode()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateCurrentNode()")
    class UpdateCurrentNode {

        @Test
        @DisplayName("happy path — sets currentNode and saves")
        void updateCurrentNode_happyPath() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(workflowInstanceRepository.save(wi)).thenReturn(wi);

            // Act
            helper.updateCurrentNode(1L, 2L);

            // Assert
            assertThat(wi.getCurrentNode()).isEqualTo(node);
            verify(workflowInstanceRepository).save(wi);
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void updateCurrentNode_instanceNotFound_throws() {
            // Arrange
            when(workflowInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> helper.updateCurrentNode(99L, 1L));
        }

        @Test
        @DisplayName("exception — node not found throws RuntimeException")
        void updateCurrentNode_nodeNotFound_throws() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> helper.updateCurrentNode(1L, 99L));
        }

        @Test
        @DisplayName("edge case — optimistic lock on CANCELLED instance is silently ignored")
        void updateCurrentNode_optimisticLock_cancelledInstance_silentlyIgnored() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);

            WorkflowInstance cancelledFresh = pendingInstance(1L);
            cancelledFresh.setStatus(WorkflowInstanceStatus.CANCELLED);

            when(workflowInstanceRepository.findById(1L))
                    .thenReturn(Optional.of(wi))
                    .thenReturn(Optional.of(cancelledFresh)); // re-fetch after lock exception
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(workflowInstanceRepository.save(wi))
                    .thenThrow(new ObjectOptimisticLockingFailureException(WorkflowInstance.class, 1L));

            // Act — should not rethrow
            assertDoesNotThrow(() -> helper.updateCurrentNode(1L, 2L));
        }

        @Test
        @DisplayName("edge case — optimistic lock on FAILED instance is silently ignored")
        void updateCurrentNode_optimisticLock_failedInstance_silentlyIgnored() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);
            WorkflowInstance failedFresh = pendingInstance(1L);
            failedFresh.setStatus(WorkflowInstanceStatus.FAILED);

            when(workflowInstanceRepository.findById(1L))
                    .thenReturn(Optional.of(wi))
                    .thenReturn(Optional.of(failedFresh));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(workflowInstanceRepository.save(wi))
                    .thenThrow(new ObjectOptimisticLockingFailureException(WorkflowInstance.class, 1L));

            // Act & Assert
            assertDoesNotThrow(() -> helper.updateCurrentNode(1L, 2L));
        }

        @Test
        @DisplayName("edge case — optimistic lock on RUNNING instance is rethrown")
        void updateCurrentNode_optimisticLock_runningInstance_rethrows() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);
            WorkflowInstance runningFresh = pendingInstance(1L);
            runningFresh.setStatus(WorkflowInstanceStatus.RUNNING);

            when(workflowInstanceRepository.findById(1L))
                    .thenReturn(Optional.of(wi))
                    .thenReturn(Optional.of(runningFresh));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(workflowInstanceRepository.save(wi))
                    .thenThrow(new ObjectOptimisticLockingFailureException(WorkflowInstance.class, 1L));

            // Act & Assert
            assertThrows(ObjectOptimisticLockingFailureException.class,
                    () -> helper.updateCurrentNode(1L, 2L));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // executeNode()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("executeNode()")
    class ExecuteNode {

        @Test
        @DisplayName("happy path — handler succeeds, node marked COMPLETED")
        void executeNode_handlerSucceeds_markedCompleted() throws Exception {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);
            NodeInstance ni = pendingNodeInstance(10L, wi, node);
            NodeHandler handler = mock(NodeHandler.class);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.saveAndFlush(ni)).thenReturn(ni);
            when(handler.execute(node, ni)).thenReturn("output-data");
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            // Act
            NodeInstance result = helper.executeNode(1L, 2L, "input-data", handler);

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
            assertThat(result.getOutputData()).isEqualTo("output-data");
        }

        @Test
        @DisplayName("happy path — APPROVAL signal marks node WAITING_APPROVAL")
        void executeNode_approvalSignal_markedWaitingApproval() throws Exception {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.APPROVAL, 0);
            NodeInstance ni = pendingNodeInstance(10L, wi, node);
            NodeHandler handler = mock(NodeHandler.class);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.saveAndFlush(ni)).thenReturn(ni);
            when(handler.execute(node, ni)).thenReturn(ApprovalNodeHandler.APPROVAL_SIGNAL);
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            // Act
            NodeInstance result = helper.executeNode(1L, 2L, "input", handler);

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.WAITING_APPROVAL);
        }

        @Test
        @DisplayName("edge case — handler throws Exception, node marked FAILED")
        void executeNode_handlerThrows_markedFailed() throws Exception {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.GPT, 0);
            NodeInstance ni = pendingNodeInstance(10L, wi, node);
            NodeHandler handler = mock(NodeHandler.class);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.saveAndFlush(ni)).thenReturn(ni);
            when(handler.execute(node, ni)).thenThrow(new RuntimeException("GPT timeout"));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            // Act
            NodeInstance result = helper.executeNode(1L, 2L, "input", handler);

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.FAILED);
            assertThat(result.getErrorMessage()).contains("GPT timeout");
        }

        @Test
        @DisplayName("edge case — already COMPLETED node is returned as-is without re-execution")
        void executeNode_alreadyCompleted_returnedWithoutExecution() throws Exception {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);
            NodeInstance ni = pendingNodeInstance(10L, wi, node);
            ni.setStatus(NodeInstanceStatus.COMPLETED);
            NodeHandler handler = mock(NodeHandler.class);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.of(ni));

            // Act
            NodeInstance result = helper.executeNode(1L, 2L, "input", handler);

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
            verify(handler, never()).execute(any(), any());
        }

        @Test
        @DisplayName("edge case — already REJECTED node is returned as-is without re-execution")
        void executeNode_alreadyRejected_returnedWithoutExecution() throws Exception {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.APPROVAL, 0);
            NodeInstance ni = pendingNodeInstance(10L, wi, node);
            ni.setStatus(NodeInstanceStatus.REJECTED);
            NodeHandler handler = mock(NodeHandler.class);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.of(ni));

            // Act
            NodeInstance result = helper.executeNode(1L, 2L, "input", handler);

            // Assert
            assertThat(result.getStatus()).isEqualTo(NodeInstanceStatus.REJECTED);
            verify(handler, never()).execute(any(), any());
        }

        @Test
        @DisplayName("edge case — node instance not in DB is created and persisted")
        void executeNode_nodeInstanceNotFound_createdAndPersisted() throws Exception {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);
            NodeHandler handler = mock(NodeHandler.class);

            NodeInstance savedNi = pendingNodeInstance(10L, wi, node);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.empty());
            when(nodeInstanceRepository.save(any(NodeInstance.class))).thenReturn(savedNi);
            when(nodeInstanceRepository.saveAndFlush(any(NodeInstance.class))).thenReturn(savedNi);
            when(handler.execute(eq(node), any())).thenReturn("result");

            // Act
            NodeInstance result = helper.executeNode(1L, 2L, "fresh-input", handler);

            // Assert — at least two saves: one for creation, one for completion
            verify(nodeInstanceRepository, atLeast(2)).save(any(NodeInstance.class));
        }

        @Test
        @DisplayName("edge case — null handler output falls back to inputData for outputData")
        void executeNode_nullOutput_outputDataFallsToInput() throws Exception {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);
            NodeInstance ni = pendingNodeInstance(10L, wi, node);
            ni.setInputData("my-input");
            NodeHandler handler = mock(NodeHandler.class);

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeRepository.findById(2L)).thenReturn(Optional.of(node));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.saveAndFlush(ni)).thenReturn(ni);
            when(handler.execute(node, ni)).thenReturn(null);
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);

            // Act
            NodeInstance result = helper.executeNode(1L, 2L, "my-input", handler);

            // Assert — markCompleted(null) falls back to inputData
            assertThat(result.getOutputData()).isEqualTo("my-input");
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // pauseInstance()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("pauseInstance()")
    class PauseInstance {

        @Test
        @DisplayName("happy path — status set to PAUSED and saved")
        void pauseInstance_happyPath() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            wi.setStatus(WorkflowInstanceStatus.RUNNING);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(workflowInstanceRepository.save(wi)).thenReturn(wi);

            // Act
            helper.pauseInstance(1L);

            // Assert
            assertThat(wi.getStatus()).isEqualTo(WorkflowInstanceStatus.PAUSED);
            verify(workflowInstanceRepository).save(wi);
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void pauseInstance_notFound_throws() {
            // Arrange
            when(workflowInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> helper.pauseInstance(99L));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // completeInstance()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeInstance()")
    class CompleteInstance {

        @Test
        @DisplayName("happy path — status COMPLETED and outputData set")
        void completeInstance_happyPath() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            wi.setStatus(WorkflowInstanceStatus.RUNNING);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(workflowInstanceRepository.save(wi)).thenReturn(wi);

            // Act
            helper.completeInstance(1L, "final-output");

            // Assert
            assertThat(wi.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
            assertThat(wi.getOutputData()).isEqualTo("final-output");
            verify(workflowInstanceRepository).save(wi);
        }

        @Test
        @DisplayName("exception — instance not found throws RuntimeException")
        void completeInstance_notFound_throws() {
            // Arrange
            when(workflowInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThrows(RuntimeException.class, () -> helper.completeInstance(99L, "out"));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // failInstance()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("failInstance()")
    class FailInstance {

        @Test
        @DisplayName("happy path — status set to FAILED with error details")
        void failInstance_happyPath() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            wi.setStatus(WorkflowInstanceStatus.RUNNING);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(workflowInstanceRepository.save(wi)).thenReturn(wi);

            // Act
            helper.failInstance(1L, "Something broke", "stack trace");

            // Assert
            assertThat(wi.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
            assertThat(wi.getErrorMessage()).isEqualTo("Something broke");
            assertThat(wi.getErrorStackTrace()).isEqualTo("stack trace");
            verify(workflowInstanceRepository).save(wi);
        }

        @Test
        @DisplayName("edge case — instance not found is silently skipped (no exception)")
        void failInstance_notFound_silentlySkipped() {
            // Arrange
            when(workflowInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert — must NOT throw
            assertDoesNotThrow(() -> helper.failInstance(99L, "error", "trace"));
            verify(workflowInstanceRepository, never()).save(any());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // resetForRetry()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resetForRetry()")
    class ResetForRetry {

        @Test
        @DisplayName("happy path — FAILED node and instance reset to PENDING")
        void resetForRetry_happyPath() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            wi.setStatus(WorkflowInstanceStatus.FAILED);

            Node node = makeNode(2L, NodeType.EMAIL, 0);
            NodeInstance ni = pendingNodeInstance(10L, wi, node);
            ni.setStatus(NodeInstanceStatus.FAILED);
            ni.setErrorMessage("old error");

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.of(ni));
            when(nodeInstanceRepository.save(ni)).thenReturn(ni);
            when(workflowInstanceRepository.save(wi)).thenReturn(wi);

            // Act
            helper.resetForRetry(1L, 2L);

            // Assert
            assertThat(ni.getStatus()).isEqualTo(NodeInstanceStatus.PENDING);
            assertThat(ni.getErrorMessage()).isNull();
            assertThat(wi.getStatus()).isEqualTo(WorkflowInstanceStatus.PENDING);
            assertThat(wi.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("exception — execution instance not found throws RuntimeException")
        void resetForRetry_executionNotFound_throws() {
            // Arrange
            when(workflowInstanceRepository.findById(99L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> helper.resetForRetry(99L, 1L));
            assertThat(ex.getMessage()).contains("Execution not found");
        }

        @Test
        @DisplayName("exception — node instance not found throws RuntimeException")
        void resetForRetry_nodeInstanceNotFound_throws() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 99L))
                    .thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> helper.resetForRetry(1L, 99L));
            assertThat(ex.getMessage()).contains("Node instance not found");
        }

        @Test
        @DisplayName("exception — node in non-FAILED state throws IllegalStateException")
        void resetForRetry_nodeNotFailed_throws() {
            // Arrange
            WorkflowInstance wi = pendingInstance(1L);
            Node node = makeNode(2L, NodeType.EMAIL, 0);
            NodeInstance ni = pendingNodeInstance(10L, wi, node);
            ni.setStatus(NodeInstanceStatus.COMPLETED); // not FAILED

            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(wi));
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 2L))
                    .thenReturn(Optional.of(ni));

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> helper.resetForRetry(1L, 2L));
            assertThat(ex.getMessage()).contains("not in FAILED state");
        }
    }
}
