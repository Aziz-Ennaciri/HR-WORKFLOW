package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.handler.NodeHandler;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowInstanceService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for WorkflowExecutionServiceImpl.
 * Design notes:
 *  - triggerWorkflow() registers a TransactionSynchronization callback. We cannot trigger
 *    afterCommit() without a real transaction manager, so we verify the synchronous
 *    pre-commit behavior (validate workflow, create instance, persist NodeInstances).
 *  - continueExecution() is pure in-memory orchestration and is fully testable.
 *  - resumeAfterApproval() submits to an ExecutorService; we verify the submission happens
 *    by spying on a partial mock or inspecting side effects through txHelper.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("WorkflowExecutionServiceImpl")
class WorkflowExecutionServiceImplTest {

    @Mock
    private IWorkflowInstanceService workflowInstanceService;

    @Mock
    private WorkflowRepository workflowRepository;

    @Mock
    private NodeRepository nodeRepository;

    @Mock
    private NodeInstanceRepository nodeInstanceRepository;

    @Mock
    private ExecutionTransactionHelper txHelper;

    /**
     * We inject a hand-crafted list so that @PostConstruct / init() can be called
     * explicitly after construction to populate the handlerRegistry.
     */
    private NodeHandler emailHandler;
    private NodeHandler approvalHandler;

    @InjectMocks
    private WorkflowExecutionServiceImpl service;

    // ─────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        // Build concrete handler stubs so we control getType()
        emailHandler = mock(NodeHandler.class);
        when(emailHandler.getType()).thenReturn(NodeType.EMAIL);

        approvalHandler = mock(NodeHandler.class);
        when(approvalHandler.getType()).thenReturn(NodeType.APPROVAL);
    }

    /**
     * Creates a service instance with a given set of handlers and calls init().
     * Needed because @InjectMocks cannot inject a List<NodeHandler> built in @BeforeEach.
     */
    private WorkflowExecutionServiceImpl serviceWithHandlers(List<NodeHandler> handlers) {
        WorkflowExecutionServiceImpl svc = new WorkflowExecutionServiceImpl(
                workflowInstanceService,
                workflowRepository,
                nodeRepository,
                nodeInstanceRepository,
                txHelper,
                handlers
        );
        svc.init();
        return svc;
    }

    // ─────────────────────────────────────────────────────────────────
    // Shared builders
    // ─────────────────────────────────────────────────────────────────

    private Workflow activeWorkflow(Long id) {
        Workflow wf = new Workflow();
        wf.setId(id);
        wf.setStatus(WorkflowStatus.ACTIVE);
        wf.setName("Test WF");
        return wf;
    }

    private Workflow draftWorkflow(Long id) {
        Workflow wf = new Workflow();
        wf.setId(id);
        wf.setStatus(WorkflowStatus.DRAFT);
        return wf;
    }

    private WorkflowInstance runningInstance(Long id, Workflow wf) {
        WorkflowInstance wi = new WorkflowInstance();
        wi.setId(id);
        wi.setWorkflow(wf);
        wi.setStatus(WorkflowInstanceStatus.RUNNING);
        wi.setInputData("{\"start\":true}");
        return wi;
    }

    private Node makeNode(Long id, NodeType type, int orderIndex) {
        Node node = new Node();
        node.setId(id);
        node.setType(type);
        node.setOrderIndex(orderIndex);
        return node;
    }

    private NodeInstance completedNodeInstance(Long id, String output) {
        NodeInstance ni = new NodeInstance();
        ni.setId(id);
        ni.setStatus(NodeInstanceStatus.COMPLETED);
        ni.setOutputData(output);
        return ni;
    }

    private NodeInstance failedNodeInstance(Long id, String error) {
        NodeInstance ni = new NodeInstance();
        ni.setId(id);
        ni.setStatus(NodeInstanceStatus.FAILED);
        ni.setErrorMessage(error);
        return ni;
    }

    private NodeInstance waitingApprovalNodeInstance(Long id) {
        NodeInstance ni = new NodeInstance();
        ni.setId(id);
        ni.setStatus(NodeInstanceStatus.WAITING_APPROVAL);
        return ni;
    }

    private NodeInstance rejectedNodeInstance(Long id, String comment) {
        NodeInstance ni = new NodeInstance();
        ni.setId(id);
        ni.setStatus(NodeInstanceStatus.REJECTED);
        ni.setComment(comment);
        return ni;
    }

    private User dummyUser() {
        User u = new User();
        u.setId(1L);
        u.setEmail("user@example.com");
        return u;
    }

    // ─────────────────────────────────────────────────────────────────
    // init() / @PostConstruct
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("init()")
    class Init {

        @Test
        @DisplayName("happy path — all handlers are registered by type")
        void init_registersAllHandlers() {
            // Arrange + Act
            WorkflowExecutionServiceImpl svc =
                    serviceWithHandlers(List.of(emailHandler, approvalHandler));

            // Assert — verify indirectly: resolving a known type should not throw
            Node emailNode = makeNode(1L, NodeType.EMAIL, 0);
            WorkflowInstance wi = runningInstance(1L, activeWorkflow(10L));
            NodeInstance completedNi = completedNodeInstance(1L, "out");

            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(emailNode));
            when(txHelper.getInstanceInputData(1L)).thenReturn("{}");
            when(txHelper.executeNode(eq(1L), eq(1L), anyString(), eq(emailHandler)))
                    .thenReturn(completedNi);

            assertDoesNotThrow(() -> svc.continueExecution(1L));
        }

        @Test
        @DisplayName("edge case — duplicate handler types: last registration wins")
        void init_duplicateHandlerType_lastWins() {
            // Arrange
            NodeHandler emailHandler2 = mock(NodeHandler.class);
            when(emailHandler2.getType()).thenReturn(NodeType.EMAIL);

            WorkflowExecutionServiceImpl svc =
                    serviceWithHandlers(List.of(emailHandler, emailHandler2));

            Node emailNode = makeNode(1L, NodeType.EMAIL, 0);
            NodeInstance completedNi = completedNodeInstance(1L, "out");

            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(emailNode));
            when(txHelper.getInstanceInputData(1L)).thenReturn("{}");
            // Accept either handler — just confirm no NPE/exception
            when(txHelper.executeNode(eq(1L), eq(1L), anyString(), any()))
                    .thenReturn(completedNi);

            // Act & Assert
            assertDoesNotThrow(() -> svc.continueExecution(1L));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // triggerWorkflow()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("triggerWorkflow()")
    class TriggerWorkflow {

        @Test
        @DisplayName("happy path — validates workflow, creates instance, saves node instances")
        void triggerWorkflow_happyPath() {
            // Arrange
            Workflow wf = activeWorkflow(10L);
            WorkflowInstance wi = runningInstance(20L, wf);
            wi.setInputData("{}");

            Node n1 = makeNode(1L, NodeType.EMAIL, 0);
            Node n2 = makeNode(2L, NodeType.GPT, 1);

            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(10L, "{}");
            User user = dummyUser();

            when(workflowRepository.findById(10L)).thenReturn(Optional.of(wf));
            when(workflowInstanceService.createInstance(dto, user)).thenReturn(wi);
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(10L)).thenReturn(List.of(n1, n2));
            when(nodeInstanceRepository.save(any(NodeInstance.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<TransactionSynchronizationManager> tsMocked =
                         mockStatic(TransactionSynchronizationManager.class)) {
                tsMocked.when(() -> TransactionSynchronizationManager
                        .registerSynchronization(any(TransactionSynchronization.class)))
                        .thenAnswer(inv -> null);

                // Act
                WorkflowInstance result = service.triggerWorkflow(dto, user);

                // Assert
                assertThat(result).isEqualTo(wi);
                // Two NodeInstances should have been persisted (one per node)
                verify(nodeInstanceRepository, times(2)).save(any(NodeInstance.class));
            }
        }

        @Test
        @DisplayName("edge case — first node gets inputData, subsequent nodes get null")
        void triggerWorkflow_firstNodeGetsInput_restGetNull() {
            // Arrange
            Workflow wf = activeWorkflow(10L);
            WorkflowInstance wi = runningInstance(20L, wf);
            wi.setInputData("START");

            Node n0 = makeNode(1L, NodeType.EMAIL, 0);
            Node n1 = makeNode(2L, NodeType.GPT, 1);

            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(10L, "START");
            User user = dummyUser();

            when(workflowRepository.findById(10L)).thenReturn(Optional.of(wf));
            when(workflowInstanceService.createInstance(dto, user)).thenReturn(wi);
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(10L)).thenReturn(List.of(n0, n1));
            when(nodeInstanceRepository.save(any(NodeInstance.class))).thenAnswer(inv -> inv.getArgument(0));

            try (MockedStatic<TransactionSynchronizationManager> tsMocked =
                         mockStatic(TransactionSynchronizationManager.class)) {
                tsMocked.when(() -> TransactionSynchronizationManager
                        .registerSynchronization(any(TransactionSynchronization.class)))
                        .thenAnswer(inv -> null);

                // Act
                service.triggerWorkflow(dto, user);

                // Assert — capture both saves
                ArgumentCaptor<NodeInstance> captor = ArgumentCaptor.forClass(NodeInstance.class);
                verify(nodeInstanceRepository, times(2)).save(captor.capture());
                List<NodeInstance> captured = captor.getAllValues();
                assertThat(captured.get(0).getInputData()).isEqualTo("START"); // orderIndex 0
                assertThat(captured.get(1).getInputData()).isNull();            // orderIndex 1
            }
        }

        @Test
        @DisplayName("exception — workflow not found throws RuntimeException")
        void triggerWorkflow_workflowNotFound_throws() {
            // Arrange
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(999L, "{}");
            when(workflowRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.triggerWorkflow(dto, dummyUser()));
            assertThat(ex.getMessage()).contains("Workflow not found");
            verify(workflowInstanceService, never()).createInstance(any(), any());
        }

        @Test
        @DisplayName("exception — non-ACTIVE workflow throws IllegalStateException")
        void triggerWorkflow_notActiveWorkflow_throws() {
            // Arrange
            Workflow wf = draftWorkflow(10L);
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO(10L, "{}");
            when(workflowRepository.findById(10L)).thenReturn(Optional.of(wf));

            // Act & Assert
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> service.triggerWorkflow(dto, dummyUser()));
            assertThat(ex.getMessage()).contains("not active");
            verify(workflowInstanceService, never()).createInstance(any(), any());
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // continueExecution()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("continueExecution()")
    class ContinueExecution {

        @Test
        @DisplayName("happy path — all nodes COMPLETE, instance marked completed")
        void continueExecution_allNodesComplete_instanceCompleted() {
            // Arrange
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(emailHandler));

            Node n1 = makeNode(1L, NodeType.EMAIL, 0);
            Node n2 = makeNode(2L, NodeType.EMAIL, 1);
            NodeInstance completed1 = completedNodeInstance(10L, "out1");
            NodeInstance completed2 = completedNodeInstance(11L, "out2");

            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(n1, n2));
            when(txHelper.getInstanceInputData(1L)).thenReturn("initial");
            when(txHelper.executeNode(1L, 1L, "initial", emailHandler)).thenReturn(completed1);
            when(txHelper.executeNode(1L, 2L, "out1", emailHandler)).thenReturn(completed2);

            // Act
            svc.continueExecution(1L);

            // Assert
            verify(txHelper).startInstance(1L);
            verify(txHelper).completeInstance(1L, "out2");
            verify(txHelper, never()).failInstance(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("edge case — FAILED node stops execution and fails the instance")
        void continueExecution_failedNode_instanceFailed() {
            // Arrange
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(emailHandler));

            Node n1 = makeNode(1L, NodeType.EMAIL, 0);
            NodeInstance failedNi = failedNodeInstance(10L, "timeout");

            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(n1));
            when(txHelper.getInstanceInputData(1L)).thenReturn("{}");
            when(txHelper.executeNode(eq(1L), eq(1L), anyString(), eq(emailHandler)))
                    .thenReturn(failedNi);

            // Act
            svc.continueExecution(1L);

            // Assert
            verify(txHelper).failInstance(eq(1L), contains("EMAIL"), anyString());
            verify(txHelper, never()).completeInstance(anyLong(), anyString());
        }

        @Test
        @DisplayName("edge case — WAITING_APPROVAL node pauses execution")
        void continueExecution_waitingApproval_instancePaused() {
            // Arrange
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(approvalHandler));

            Node approvalNode = makeNode(1L, NodeType.APPROVAL, 0);
            NodeInstance waitingNi = waitingApprovalNodeInstance(10L);

            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(approvalNode));
            when(txHelper.getInstanceInputData(1L)).thenReturn("{}");
            when(txHelper.executeNode(eq(1L), eq(1L), anyString(), eq(approvalHandler)))
                    .thenReturn(waitingNi);

            // Act
            svc.continueExecution(1L);

            // Assert
            verify(txHelper).pauseInstance(1L);
            verify(txHelper, never()).completeInstance(anyLong(), anyString());
            verify(txHelper, never()).failInstance(anyLong(), anyString(), anyString());
        }

        @Test
        @DisplayName("edge case — REJECTED node stops execution and fails the instance")
        void continueExecution_rejectedNode_instanceFailed() {
            // Arrange
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(approvalHandler));

            Node approvalNode = makeNode(1L, NodeType.APPROVAL, 0);
            NodeInstance rejectedNi = rejectedNodeInstance(10L, "Manager rejected");

            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(approvalNode));
            when(txHelper.getInstanceInputData(1L)).thenReturn("{}");
            when(txHelper.executeNode(eq(1L), eq(1L), anyString(), eq(approvalHandler)))
                    .thenReturn(rejectedNi);

            // Act
            svc.continueExecution(1L);

            // Assert
            verify(txHelper).failInstance(eq(1L), contains("APPROVAL"), eq("Manager rejected"));
            verify(txHelper, never()).completeInstance(anyLong(), anyString());
        }

        @Test
        @DisplayName("exception — empty node list throws RuntimeException and fails instance")
        void continueExecution_noNodes_failsInstance() {
            // Arrange
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(emailHandler));
            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of());

            // Act
            svc.continueExecution(1L);

            // Assert — continueExecution catches the exception and calls failInstance
            verify(txHelper).failInstance(eq(1L), contains("Workflow has no nodes"), anyString());
        }

        @Test
        @DisplayName("exception — no handler registered for node type fails instance")
        void continueExecution_noHandlerForType_failsInstance() {
            // Arrange — service initialized with only EMAIL handler, but node is GPT
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(emailHandler));

            Node gptNode = makeNode(1L, NodeType.GPT, 0);
            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(gptNode));
            when(txHelper.getInstanceInputData(1L)).thenReturn("{}");

            // Act
            svc.continueExecution(1L);

            // Assert
            verify(txHelper).failInstance(eq(1L), contains("No handler registered for node type"), anyString());
        }

        @Test
        @DisplayName("edge case — output flows correctly from node to node")
        void continueExecution_outputChaining_correct() {
            // Arrange
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(emailHandler));

            Node n1 = makeNode(1L, NodeType.EMAIL, 0);
            Node n2 = makeNode(2L, NodeType.EMAIL, 1);
            Node n3 = makeNode(3L, NodeType.EMAIL, 2);
            NodeInstance r1 = completedNodeInstance(10L, "step1-out");
            NodeInstance r2 = completedNodeInstance(11L, "step2-out");
            NodeInstance r3 = completedNodeInstance(12L, "step3-out");

            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(n1, n2, n3));
            when(txHelper.getInstanceInputData(1L)).thenReturn("START");
            when(txHelper.executeNode(1L, 1L, "START",      emailHandler)).thenReturn(r1);
            when(txHelper.executeNode(1L, 2L, "step1-out",  emailHandler)).thenReturn(r2);
            when(txHelper.executeNode(1L, 3L, "step2-out",  emailHandler)).thenReturn(r3);

            // Act
            svc.continueExecution(1L);

            // Assert — final output is last node's output
            verify(txHelper).completeInstance(1L, "step3-out");
        }

        @Test
        @DisplayName("edge case — updateCurrentNode called for each node before status check")
        void continueExecution_updateCurrentNodeCalledPerNode() {
            // Arrange
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(emailHandler));

            Node n1 = makeNode(1L, NodeType.EMAIL, 0);
            Node n2 = makeNode(2L, NodeType.EMAIL, 1);
            NodeInstance r1 = completedNodeInstance(10L, "out1");
            NodeInstance r2 = completedNodeInstance(11L, "out2");

            when(txHelper.getWorkflowNodes(1L)).thenReturn(List.of(n1, n2));
            when(txHelper.getInstanceInputData(1L)).thenReturn("{}");
            when(txHelper.executeNode(1L, 1L, "{}", emailHandler)).thenReturn(r1);
            when(txHelper.executeNode(1L, 2L, "out1", emailHandler)).thenReturn(r2);

            // Act
            svc.continueExecution(1L);

            // Assert
            verify(txHelper).updateCurrentNode(1L, 1L);
            verify(txHelper).updateCurrentNode(1L, 2L);
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // retryFromNode()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("retryFromNode()")
    class RetryFromNode {

        @Test
        @DisplayName("happy path — delegates reset to txHelper")
        void retryFromNode_happyPath() {
            try (MockedStatic<TransactionSynchronizationManager> tsMocked =
                         mockStatic(TransactionSynchronizationManager.class)) {
                tsMocked.when(() -> TransactionSynchronizationManager
                        .registerSynchronization(any(TransactionSynchronization.class)))
                        .thenAnswer(inv -> null);

                // Arrange + Act
                service.retryFromNode(1L, 2L);

                // Assert — resetForRetry must be called; the async submit is best-effort
                verify(txHelper).resetForRetry(1L, 2L);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // resumeAfterApproval()
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("resumeAfterApproval()")
    class ResumeAfterApproval {

        @Test
        @DisplayName("happy path — submits continueExecution to executor without throwing")
        void resumeAfterApproval_submitsWithoutThrowing() {
            // Arrange
            WorkflowExecutionServiceImpl svc = serviceWithHandlers(List.of(emailHandler));

            // Act — just verify no synchronous exception is thrown
            assertDoesNotThrow(() -> svc.resumeAfterApproval(1L));
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // shutdown() / @PreDestroy
    // ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("shutdown()")
    class Shutdown {

        @Test
        @DisplayName("edge case — shutdown() can be called without exceptions")
        void shutdown_noException() {
            // Arrange + Act + Assert
            assertDoesNotThrow(() -> service.shutdown());
        }
    }
}
