package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.config.ExecutionProperties;
import ma.rh.ai.hr_workflow.execution.handler.NodeExecutionContext;
import ma.rh.ai.hr_workflow.execution.handler.NodeHandler;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkflowExecutionServiceImpl")
class WorkflowExecutionServiceImplTest {

    @Mock private IWorkflowInstanceService workflowInstanceService;
    @Mock private WorkflowRepository workflowRepository;
    @Mock private NodeRepository nodeRepository;
    @Mock private NodeInstanceRepository nodeInstanceRepository;
    @Mock private ExecutionTransactionHelper txHelper;
    @Mock private NodeHandler gptHandler;

    /** Records submitted work instead of running it, so tests decide when (and whether) it runs. */
    private final List<Runnable> submitted = new ArrayList<>();
    private TaskExecutor executor = submitted::add;

    private WorkflowExecutionServiceImpl service;

    private static final int MAX_ATTEMPTS = 3;

    @BeforeEach
    void setUp() {
        lenient().when(gptHandler.getType()).thenReturn(NodeType.GPT);
        service = newService(executor);
    }

    private WorkflowExecutionServiceImpl newService(TaskExecutor taskExecutor) {
        ExecutionProperties properties = new ExecutionProperties(
                new ExecutionProperties.Worker(1, 1, 1, Duration.ofSeconds(1)),
                new ExecutionProperties.Retry(MAX_ATTEMPTS, Duration.ofMillis(1), 2.0),
                new ExecutionProperties.Sweeper(false, Duration.ofSeconds(30), Duration.ofSeconds(30)));
        return new WorkflowExecutionServiceImpl(workflowInstanceService, workflowRepository, nodeRepository,
                nodeInstanceRepository, txHelper, List.of(gptHandler), taskExecutor, properties);
    }

    private static NodeExecutionContext gptStep(long nodeInstanceId) {
        return new NodeExecutionContext(1L, nodeInstanceId, NodeType.GPT, "{}", "in", "in", "Hiring");
    }

    @Nested
    @DisplayName("triggerWorkflow()")
    class Trigger {

        private Workflow workflow(WorkflowStatus status) {
            Workflow workflow = new Workflow();
            workflow.setId(5L);
            workflow.setStatus(status);
            return workflow;
        }

        @Test
        @DisplayName("freezes each node's type and config into a PENDING step, then queues the execution")
        void trigger_snapshotsNodesAndSubmits() {
            Node drive = new Node();
            drive.setId(20L);
            drive.setType(NodeType.DRIVE);
            drive.setOrderIndex(1);
            drive.setConfigJson("{\"folderId\":\"cvs\"}");
            WorkflowInstance created = new WorkflowInstance();
            created.setId(1L);
            User user = new User();
            user.setEmail("hr@test.com");
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(5L);

            when(workflowRepository.findById(5L)).thenReturn(Optional.of(workflow(WorkflowStatus.ACTIVE)));
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(5L)).thenReturn(List.of(drive));
            when(workflowInstanceService.createInstance(dto, user)).thenReturn(created);

            assertThat(service.triggerWorkflow(dto, user)).isSameAs(created);

            ArgumentCaptor<NodeInstance> step = ArgumentCaptor.forClass(NodeInstance.class);
            verify(nodeInstanceRepository).save(step.capture());
            assertThat(step.getValue().getNodeType()).isEqualTo(NodeType.DRIVE);
            assertThat(step.getValue().getConfigSnapshot()).isEqualTo("{\"folderId\":\"cvs\"}");
            assertThat(step.getValue().getNode()).isSameAs(drive);
            assertThat(step.getValue().getStatus()).isEqualTo(NodeInstanceStatus.PENDING);
            assertThat(step.getValue().getExecutionOrder()).isEqualTo(1);
            assertThat(submitted).hasSize(1);
        }

        @Test
        @DisplayName("a non-ACTIVE workflow cannot be triggered")
        void trigger_inactive_throws() {
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(5L);
            when(workflowRepository.findById(5L)).thenReturn(Optional.of(workflow(WorkflowStatus.DRAFT)));

            assertThrows(IllegalStateException.class, () -> service.triggerWorkflow(dto, new User()));
            verifyNoInteractions(workflowInstanceService);
        }

        @Test
        @DisplayName("a deleted workflow is reported as not found")
        void trigger_deleted_throws() {
            Workflow deleted = workflow(WorkflowStatus.ACTIVE);
            deleted.setDeleted(true);
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(5L);
            when(workflowRepository.findById(5L)).thenReturn(Optional.of(deleted));

            assertThrows(ResourceNotFoundException.class, () -> service.triggerWorkflow(dto, new User()));
        }

        @Test
        @DisplayName("a workflow without nodes is rejected before any execution is created")
        void trigger_noNodes_throws() {
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(5L);
            when(workflowRepository.findById(5L)).thenReturn(Optional.of(workflow(WorkflowStatus.ACTIVE)));
            when(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(5L)).thenReturn(List.of());

            assertThrows(IllegalStateException.class, () -> service.triggerWorkflow(dto, new User()));
            verifyNoInteractions(workflowInstanceService);
        }
    }

    @Nested
    @DisplayName("runExecution()")
    class Run {

        @Test
        @DisplayName("does nothing when the execution cannot be claimed")
        void run_notClaimed() {
            when(txHelper.claim(1L)).thenReturn(false);

            service.runExecution(1L);

            verify(txHelper, never()).nextStep(anyLong());
        }

        @Test
        @DisplayName("losing the claim race to another worker is not an error")
        void run_claimRaceLost() {
            when(txHelper.claim(1L)).thenThrow(new ObjectOptimisticLockingFailureException(WorkflowInstance.class, 1L));

            service.runExecution(1L);

            verify(txHelper, never()).nextStep(anyLong());
            verify(txHelper, never()).failInstance(anyLong(), any(), any());
        }

        @Test
        @DisplayName("runs steps in order until none is left")
        void run_allSteps() throws Exception {
            when(txHelper.claim(1L)).thenReturn(true);
            when(txHelper.nextStep(1L)).thenReturn(Optional.of(gptStep(10)), Optional.of(gptStep(11)), Optional.empty());
            when(gptHandler.execute(any())).thenReturn("out-10", "out-11");
            when(txHelper.recordResult(anyLong(), any())).thenReturn(true);

            service.runExecution(1L);

            verify(txHelper).recordResult(10L, "out-10");
            verify(txHelper).recordResult(11L, "out-11");
            verify(txHelper, times(3)).nextStep(1L);
        }

        @Test
        @DisplayName("stops when a step pauses or the execution was cancelled")
        void run_stopsWhenRecordResultSaysSo() throws Exception {
            when(txHelper.claim(1L)).thenReturn(true);
            when(txHelper.nextStep(1L)).thenReturn(Optional.of(gptStep(10)));
            when(gptHandler.execute(any())).thenReturn("out");
            when(txHelper.recordResult(10L, "out")).thenReturn(false);

            service.runExecution(1L);

            verify(txHelper, times(1)).nextStep(1L);
        }

        @Test
        @DisplayName("a non-transient error fails the step immediately, without retrying")
        void run_permanentError_noRetry() throws Exception {
            when(txHelper.claim(1L)).thenReturn(true);
            when(txHelper.nextStep(1L)).thenReturn(Optional.of(gptStep(10)));
            when(gptHandler.execute(any())).thenThrow(new RuntimeException("API key is required"));

            service.runExecution(1L);

            verify(gptHandler, times(1)).execute(any());
            verify(txHelper, never()).recordRetry(anyLong(), any());
            verify(txHelper).recordFailure(eq(10L), eq("API key is required"), any());
        }

        @Test
        @DisplayName("a transient error is retried, and the step succeeds on a later attempt")
        void run_transientError_retriedThenSucceeds() throws Exception {
            when(txHelper.claim(1L)).thenReturn(true);
            when(txHelper.nextStep(1L)).thenReturn(Optional.of(gptStep(10)), Optional.empty());
            when(gptHandler.execute(any()))
                    .thenThrow(new RuntimeException("GPT analysis failed", new ResourceAccessException("Read timed out")))
                    .thenReturn("analysis");
            when(txHelper.recordResult(10L, "analysis")).thenReturn(true);

            service.runExecution(1L);

            verify(txHelper).recordRetry(10L, "GPT analysis failed");
            verify(txHelper).recordResult(10L, "analysis");
            verify(txHelper, never()).recordFailure(anyLong(), any(), any());
        }

        @Test
        @DisplayName("a transient error that persists fails the step after the max attempts")
        void run_transientError_exhaustsAttempts() throws Exception {
            when(txHelper.claim(1L)).thenReturn(true);
            when(txHelper.nextStep(1L)).thenReturn(Optional.of(gptStep(10)));
            when(gptHandler.execute(any())).thenThrow(new ResourceAccessException("Connection refused"));

            service.runExecution(1L);

            verify(gptHandler, times(MAX_ATTEMPTS)).execute(any());
            verify(txHelper, times(MAX_ATTEMPTS - 1)).recordRetry(eq(10L), any());
            verify(txHelper).recordFailure(eq(10L), eq("Connection refused"), any());
        }

        @Test
        @DisplayName("a step type without a handler fails the step")
        void run_missingHandler() {
            when(txHelper.claim(1L)).thenReturn(true);
            when(txHelper.nextStep(1L)).thenReturn(Optional.of(
                    new NodeExecutionContext(1L, 10L, NodeType.EMAIL, "{}", null, null, "Hiring")));

            service.runExecution(1L);

            verify(txHelper).recordFailure(eq(10L), contains("No handler"), isNull());
        }

        @Test
        @DisplayName("an unexpected engine error fails the execution")
        void run_unexpectedError_failsExecution() {
            when(txHelper.claim(1L)).thenReturn(true);
            when(txHelper.nextStep(1L)).thenThrow(new IllegalStateException("db down"));

            service.runExecution(1L);

            verify(txHelper).failInstance(eq(1L), eq("Error: db down"), contains("IllegalStateException"));
        }
    }

    @Nested
    @DisplayName("scheduling")
    class Scheduling {

        @Test
        @DisplayName("continue, resume and retry only queue work; claiming decides whether it runs")
        void continueAndResume_submit() {
            service.continueExecution(1L);
            service.resumeAfterApproval(1L);
            when(txHelper.resetForRetry(10L)).thenReturn(1L);
            service.retryStep(10L);

            assertThat(submitted).hasSize(3);
            verify(txHelper, never()).claim(anyLong());
        }

        @Test
        @DisplayName("retryFromNode resolves the step of that node and retries it")
        void retryFromNode_resolvesStep() {
            NodeInstance step = new NodeInstance();
            step.setId(10L);
            when(nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(1L, 20L)).thenReturn(Optional.of(step));
            when(txHelper.resetForRetry(10L)).thenReturn(1L);

            service.retryFromNode(1L, 20L);

            assertThat(submitted).hasSize(1);
        }

        @Test
        @DisplayName("a saturated executor does not fail the request; the sweeper picks the execution up later")
        void submit_rejected_isSwallowed() {
            WorkflowExecutionServiceImpl saturated = newService(task -> {
                throw new TaskRejectedException("queue full");
            });

            saturated.continueExecution(1L);
        }
    }

    @Test
    @DisplayName("isTransient() recognises network errors, 5xx and 429 anywhere in the cause chain")
    void isTransient_classification() {
        assertThat(WorkflowExecutionServiceImpl.isTransient(new ResourceAccessException("timeout"))).isTrue();
        assertThat(WorkflowExecutionServiceImpl.isTransient(new RuntimeException(new SocketTimeoutException()))).isTrue();
        assertThat(WorkflowExecutionServiceImpl.isTransient(
                HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "bad gateway", null, null, null))).isTrue();
        assertThat(WorkflowExecutionServiceImpl.isTransient(
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "slow down", null, null, null))).isTrue();

        assertThat(WorkflowExecutionServiceImpl.isTransient(
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "bad key", null, null, null))).isFalse();
        assertThat(WorkflowExecutionServiceImpl.isTransient(new RuntimeException("Email sending failed"))).isFalse();
    }
}
