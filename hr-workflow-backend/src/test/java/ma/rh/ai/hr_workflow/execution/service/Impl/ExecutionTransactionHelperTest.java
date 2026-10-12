package ma.rh.ai.hr_workflow.execution.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.execution.handler.ApprovalNodeHandler;
import ma.rh.ai.hr_workflow.execution.handler.NodeExecutionContext;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExecutionTransactionHelper")
class ExecutionTransactionHelperTest {

    @Mock private WorkflowInstanceRepository workflowInstanceRepository;
    @Mock private NodeInstanceRepository nodeInstanceRepository;
    @Mock private UserRepository userRepository;

    private ExecutionTransactionHelper helper;
    private WorkflowInstance execution;

    @BeforeEach
    void setUp() {
        helper = new ExecutionTransactionHelper(workflowInstanceRepository, nodeInstanceRepository,
                userRepository, new ApprovalNodeHandler(new ObjectMapper()));

        Workflow workflow = new Workflow();
        workflow.setName("Hiring");
        execution = new WorkflowInstance();
        execution.setId(1L);
        execution.setWorkflow(workflow);
        execution.setInputData("{\"prompt\":\"rank\"}");
    }

    private NodeInstance step(long id, int order, NodeType type, NodeInstanceStatus status) {
        NodeInstance step = new NodeInstance();
        step.setId(id);
        step.setExecutionOrder(order);
        step.setNodeType(type);
        step.setConfigSnapshot("{\"step\":" + id + "}");
        step.setStatus(status);
        step.setWorkflowInstance(execution);
        return step;
    }

    private void givenExecution(WorkflowInstanceStatus status) {
        execution.setStatus(status);
        when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.of(execution));
    }

    private void givenSteps(NodeInstance... steps) {
        when(nodeInstanceRepository.findByWorkflowInstanceIdOrderByExecutionOrderAsc(1L)).thenReturn(List.of(steps));
    }

    @Nested
    @DisplayName("claim()")
    class Claim {

        @Test
        @DisplayName("PENDING execution is started and flushed (the version check happens here)")
        void claim_pending_startsExecution() {
            givenExecution(WorkflowInstanceStatus.PENDING);

            assertThat(helper.claim(1L)).isTrue();

            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.RUNNING);
            assertThat(execution.getStartedAt()).isNotNull();
            verify(workflowInstanceRepository).saveAndFlush(execution);
        }

        @Test
        @DisplayName("PAUSED execution whose approval was decided resumes, keeping its start time")
        void claim_pausedAndDecided_resumes() {
            LocalDateTime started = LocalDateTime.now().minusMinutes(5);
            execution.setStartedAt(started);
            givenExecution(WorkflowInstanceStatus.PAUSED);
            when(nodeInstanceRepository.existsByWorkflowInstanceIdAndStatus(1L, NodeInstanceStatus.WAITING_APPROVAL))
                    .thenReturn(false);

            assertThat(helper.claim(1L)).isTrue();

            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.RUNNING);
            assertThat(execution.getStartedAt()).isEqualTo(started);
        }

        @Test
        @DisplayName("PAUSED execution still waiting for approval is not claimed")
        void claim_pausedAndWaiting_refused() {
            givenExecution(WorkflowInstanceStatus.PAUSED);
            when(nodeInstanceRepository.existsByWorkflowInstanceIdAndStatus(1L, NodeInstanceStatus.WAITING_APPROVAL))
                    .thenReturn(true);

            assertThat(helper.claim(1L)).isFalse();
            verify(workflowInstanceRepository, never()).saveAndFlush(any());
        }

        @ParameterizedTest(name = "{0} execution is not claimed")
        @EnumSource(value = WorkflowInstanceStatus.class, names = {"RUNNING", "COMPLETED", "FAILED", "CANCELLED"})
        @DisplayName("running or finished executions are not claimed again")
        void claim_notClaimable(WorkflowInstanceStatus status) {
            givenExecution(status);

            assertThat(helper.claim(1L)).isFalse();
            assertThat(execution.getStatus()).isEqualTo(status);
        }

        @Test
        @DisplayName("missing execution is not claimed")
        void claim_missing() {
            when(workflowInstanceRepository.findById(1L)).thenReturn(Optional.empty());
            assertThat(helper.claim(1L)).isFalse();
        }
    }

    @Nested
    @DisplayName("nextStep()")
    class NextStep {

        @Test
        @DisplayName("first pending step starts with the execution input and its frozen config")
        void nextStep_firstStep() {
            givenExecution(WorkflowInstanceStatus.RUNNING);
            Node node = new Node();
            NodeInstance first = step(10, 1, NodeType.DRIVE, NodeInstanceStatus.PENDING);
            first.setNode(node);
            givenSteps(first, step(11, 2, NodeType.GPT, NodeInstanceStatus.PENDING));

            NodeExecutionContext context = helper.nextStep(1L).orElseThrow();

            assertThat(context.nodeInstanceId()).isEqualTo(10L);
            assertThat(context.nodeType()).isEqualTo(NodeType.DRIVE);
            assertThat(context.configJson()).isEqualTo("{\"step\":10}");
            assertThat(context.inputData()).isEqualTo("{\"prompt\":\"rank\"}");
            assertThat(context.workflowInputData()).isEqualTo("{\"prompt\":\"rank\"}");
            assertThat(context.workflowName()).isEqualTo("Hiring");
            assertThat(first.getStatus()).isEqualTo(NodeInstanceStatus.IN_PROGRESS);
            assertThat(first.getInputData()).isEqualTo("{\"prompt\":\"rank\"}");
            assertThat(execution.getCurrentNode()).isSameAs(node);
        }

        @Test
        @DisplayName("a later step receives the previous step's output")
        void nextStep_chainsOutput() {
            givenExecution(WorkflowInstanceStatus.RUNNING);
            NodeInstance done = step(10, 1, NodeType.DRIVE, NodeInstanceStatus.COMPLETED);
            done.setOutputData("drive output");
            givenSteps(done, step(11, 2, NodeType.GPT, NodeInstanceStatus.PENDING));

            NodeExecutionContext context = helper.nextStep(1L).orElseThrow();

            assertThat(context.nodeInstanceId()).isEqualTo(11L);
            assertThat(context.inputData()).isEqualTo("drive output");
        }

        @Test
        @DisplayName("when every step is done the execution completes with the last output")
        void nextStep_allDone_completes() {
            givenExecution(WorkflowInstanceStatus.RUNNING);
            NodeInstance last = step(10, 1, NodeType.GPT, NodeInstanceStatus.COMPLETED);
            last.setOutputData("final");
            givenSteps(last);

            assertThat(helper.nextStep(1L)).isEmpty();

            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
            assertThat(execution.getOutputData()).isEqualTo("final");
            assertThat(execution.getFinishedAt()).isNotNull();
        }

        @Test
        @DisplayName("a step waiting for approval pauses the execution")
        void nextStep_waitingApproval_pauses() {
            givenExecution(WorkflowInstanceStatus.RUNNING);
            givenSteps(step(10, 1, NodeType.APPROVAL, NodeInstanceStatus.WAITING_APPROVAL));

            assertThat(helper.nextStep(1L)).isEmpty();
            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.PAUSED);
        }

        @Test
        @DisplayName("a rejected step fails the execution")
        void nextStep_rejected_fails() {
            givenExecution(WorkflowInstanceStatus.RUNNING);
            givenSteps(step(10, 1, NodeType.APPROVAL, NodeInstanceStatus.REJECTED));

            assertThat(helper.nextStep(1L)).isEmpty();
            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
        }

        @Test
        @DisplayName("a step left IN_PROGRESS (outcome never recorded) fails the execution instead of re-running it")
        void nextStep_leftoverInProgress_fails() {
            givenExecution(WorkflowInstanceStatus.RUNNING);
            givenSteps(step(10, 1, NodeType.EMAIL, NodeInstanceStatus.IN_PROGRESS));

            assertThat(helper.nextStep(1L)).isEmpty();
            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
        }

        @ParameterizedTest(name = "{0} execution yields no step")
        @EnumSource(value = WorkflowInstanceStatus.class, names = "RUNNING", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("only a RUNNING execution hands out steps (cancelled/paused/finished stop the loop)")
        void nextStep_notRunning(WorkflowInstanceStatus status) {
            givenExecution(status);

            assertThat(helper.nextStep(1L)).isEmpty();
            verifyNoInteractions(nodeInstanceRepository);
        }
    }

    @Nested
    @DisplayName("recordResult()")
    class RecordResult {

        @Test
        @DisplayName("stores the output and continues while the execution is RUNNING")
        void recordResult_completes() {
            execution.setStatus(WorkflowInstanceStatus.RUNNING);
            NodeInstance running = step(10, 1, NodeType.GPT, NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(running));

            assertThat(helper.recordResult(10L, "analysis")).isTrue();

            assertThat(running.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
            assertThat(running.getOutputData()).isEqualTo("analysis");
        }

        @Test
        @DisplayName("an execution cancelled meanwhile keeps its status and stops the loop")
        void recordResult_cancelledMeanwhile_stops() {
            execution.setStatus(WorkflowInstanceStatus.CANCELLED);
            NodeInstance running = step(10, 1, NodeType.GPT, NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(running));

            assertThat(helper.recordResult(10L, "analysis")).isFalse();

            assertThat(running.getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.CANCELLED);
        }

        @Test
        @DisplayName("the approval signal pauses step and execution together and assigns the approver")
        void recordResult_approval_pausesAtomically() {
            execution.setStatus(WorkflowInstanceStatus.RUNNING);
            NodeInstance approval = step(10, 1, NodeType.APPROVAL, NodeInstanceStatus.IN_PROGRESS);
            approval.setConfigSnapshot("{\"approverEmail\":\"boss@test.com\"}");
            User boss = new User();
            boss.setEmail("boss@test.com");
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(approval));
            when(userRepository.findByEmail("boss@test.com")).thenReturn(Optional.of(boss));

            assertThat(helper.recordResult(10L, ApprovalNodeHandler.APPROVAL_SIGNAL)).isFalse();

            assertThat(approval.getStatus()).isEqualTo(NodeInstanceStatus.WAITING_APPROVAL);
            assertThat(approval.getAssignedTo()).isSameAs(boss);
            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.PAUSED);
        }
    }

    @Nested
    @DisplayName("failures and retries")
    class Failures {

        @Test
        @DisplayName("recordRetry counts the attempt and keeps the last error")
        void recordRetry_increments() {
            NodeInstance running = step(10, 1, NodeType.GPT, NodeInstanceStatus.IN_PROGRESS);
            running.setRetryCount(1);
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(running));

            helper.recordRetry(10L, "timeout");

            assertThat(running.getRetryCount()).isEqualTo(2);
            assertThat(running.getErrorMessage()).isEqualTo("timeout");
            assertThat(running.getStatus()).isEqualTo(NodeInstanceStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("recordFailure fails the step and the execution")
        void recordFailure_failsBoth() {
            execution.setStatus(WorkflowInstanceStatus.RUNNING);
            NodeInstance running = step(10, 1, NodeType.EMAIL, NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(running));

            helper.recordFailure(10L, "SMTP down", "trace");

            assertThat(running.getStatus()).isEqualTo(NodeInstanceStatus.FAILED);
            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
            assertThat(execution.getErrorMessage()).isEqualTo("Failed at EMAIL: SMTP down");
            assertThat(execution.getErrorStackTrace()).isEqualTo("trace");
        }

        @Test
        @DisplayName("recordFailure leaves a cancelled execution CANCELLED")
        void recordFailure_cancelledStaysCancelled() {
            execution.setStatus(WorkflowInstanceStatus.CANCELLED);
            NodeInstance running = step(10, 1, NodeType.EMAIL, NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(running));

            helper.recordFailure(10L, "SMTP down", null);

            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.CANCELLED);
        }

        @Test
        @DisplayName("failInstance never overwrites a finished execution")
        void failInstance_keepsFinished() {
            givenExecution(WorkflowInstanceStatus.COMPLETED);

            helper.failInstance(1L, "late error", null);

            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.COMPLETED);
        }

        @Test
        @DisplayName("failInterrupted fails a RUNNING execution and its in-flight step")
        void failInterrupted_running() {
            givenExecution(WorkflowInstanceStatus.RUNNING);
            NodeInstance inFlight = step(10, 1, NodeType.EMAIL, NodeInstanceStatus.IN_PROGRESS);
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndStatus(1L, NodeInstanceStatus.IN_PROGRESS))
                    .thenReturn(List.of(inFlight));

            helper.failInterrupted(1L);

            assertThat(inFlight.getStatus()).isEqualTo(NodeInstanceStatus.FAILED);
            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.FAILED);
            assertThat(execution.getErrorMessage()).contains("restart");
        }

        @Test
        @DisplayName("failInterrupted ignores executions that are not RUNNING")
        void failInterrupted_notRunning() {
            givenExecution(WorkflowInstanceStatus.PAUSED);

            helper.failInterrupted(1L);

            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.PAUSED);
            verifyNoInteractions(nodeInstanceRepository);
        }
    }

    @Nested
    @DisplayName("resetForRetry()")
    class ResetForRetry {

        @Test
        @DisplayName("resets the failed step and execution and picks up the node's current config")
        void resetForRetry_resets() {
            execution.setStatus(WorkflowInstanceStatus.FAILED);
            execution.setErrorMessage("boom");
            Node node = new Node();
            node.setType(NodeType.EMAIL);
            node.setConfigJson("{\"to\":\"fixed@test.com\"}");
            NodeInstance failed = step(10, 1, NodeType.EMAIL, NodeInstanceStatus.FAILED);
            failed.setNode(node);
            failed.setErrorMessage("bad address");
            failed.setRetryCount(0);
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(failed));

            assertThat(helper.resetForRetry(10L)).isEqualTo(1L);

            assertThat(failed.getStatus()).isEqualTo(NodeInstanceStatus.PENDING);
            assertThat(failed.getRetryCount()).isEqualTo(1);
            assertThat(failed.getErrorMessage()).isNull();
            assertThat(failed.getConfigSnapshot()).isEqualTo("{\"to\":\"fixed@test.com\"}");
            assertThat(execution.getStatus()).isEqualTo(WorkflowInstanceStatus.PENDING);
            assertThat(execution.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("a step whose node was deleted retries with its frozen config")
        void resetForRetry_deletedNode_keepsSnapshot() {
            execution.setStatus(WorkflowInstanceStatus.FAILED);
            NodeInstance failed = step(10, 1, NodeType.EMAIL, NodeInstanceStatus.FAILED);
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(failed));

            helper.resetForRetry(10L);

            assertThat(failed.getConfigSnapshot()).isEqualTo("{\"step\":10}");
        }

        @Test
        @DisplayName("only FAILED steps of FAILED executions can be retried")
        void resetForRetry_rejectsOtherStates() {
            execution.setStatus(WorkflowInstanceStatus.FAILED);
            NodeInstance done = step(10, 1, NodeType.EMAIL, NodeInstanceStatus.COMPLETED);
            when(nodeInstanceRepository.findById(10L)).thenReturn(Optional.of(done));
            assertThrows(IllegalStateException.class, () -> helper.resetForRetry(10L));

            execution.setStatus(WorkflowInstanceStatus.RUNNING);
            NodeInstance failed = step(11, 2, NodeType.EMAIL, NodeInstanceStatus.FAILED);
            when(nodeInstanceRepository.findById(11L)).thenReturn(Optional.of(failed));
            assertThrows(IllegalStateException.class, () -> helper.resetForRetry(11L));
        }
    }
}
