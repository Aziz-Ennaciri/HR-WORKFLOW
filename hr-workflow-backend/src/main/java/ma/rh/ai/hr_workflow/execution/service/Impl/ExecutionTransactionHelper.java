package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import ma.rh.ai.hr_workflow.execution.handler.ApprovalNodeHandler;
import ma.rh.ai.hr_workflow.execution.handler.NodeExecutionContext;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;

/**
 * The engine's database steps, each in its own short transaction. External calls (GPT, SMTP,
 * Drive) happen between these steps, never inside one, so no connection is held while waiting.
 * Both execution entities carry {@code @Version}, so concurrent writers fail instead of
 * silently overwriting each other.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionTransactionHelper {

    private static final Set<NodeInstanceStatus> DONE = EnumSet.of(NodeInstanceStatus.COMPLETED, NodeInstanceStatus.SKIPPED);
    private static final Set<NodeInstanceStatus> RUNNABLE = EnumSet.of(NodeInstanceStatus.PENDING, NodeInstanceStatus.RETRYING);

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final NodeInstanceRepository nodeInstanceRepository;
    private final UserRepository userRepository;
    private final ApprovalNodeHandler approvalNodeHandler;

    /**
     * Moves the execution to RUNNING if it may run now: PENDING, or PAUSED with no step still
     * waiting for approval. Returns false when someone else is running it or it is finished.
     * Two concurrent claims race on the version column; the loser gets an optimistic-lock failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long workflowInstanceId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId).orElse(null);
        if (instance == null) {
            return false;
        }
        boolean claimable = instance.getStatus() == WorkflowInstanceStatus.PENDING
                || (instance.getStatus() == WorkflowInstanceStatus.PAUSED
                    && !nodeInstanceRepository.existsByWorkflowInstanceIdAndStatus(
                            workflowInstanceId, NodeInstanceStatus.WAITING_APPROVAL));
        if (!claimable) {
            return false;
        }
        if (instance.getStartedAt() == null) {
            instance.start();
        } else {
            instance.setStatus(WorkflowInstanceStatus.RUNNING);
        }
        workflowInstanceRepository.saveAndFlush(instance);
        return true;
    }

    /**
     * Finds the next step to run and marks it IN_PROGRESS, feeding it the previous step's output.
     * Returns empty when the execution is over (completed, failed, cancelled, deleted) or paused.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<NodeExecutionContext> nextStep(Long workflowInstanceId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId).orElse(null);
        if (instance == null || instance.getStatus() != WorkflowInstanceStatus.RUNNING) {
            return Optional.empty();
        }

        String previousOutput = instance.getInputData();
        List<NodeInstance> steps = nodeInstanceRepository.findByWorkflowInstanceIdOrderByExecutionOrderAsc(workflowInstanceId);
        for (NodeInstance step : steps) {
            NodeInstanceStatus status = step.getStatus();
            if (DONE.contains(status)) {
                previousOutput = step.getOutputData();
                continue;
            }
            if (status == NodeInstanceStatus.WAITING_APPROVAL) {
                instance.setStatus(WorkflowInstanceStatus.PAUSED);
                return Optional.empty();
            }
            if (status == NodeInstanceStatus.REJECTED) {
                finish(instance, WorkflowInstanceStatus.FAILED, "Rejected at " + step.getNodeType(), step.getComment());
                return Optional.empty();
            }
            if (!RUNNABLE.contains(status)) {
                // FAILED or IN_PROGRESS here means the step's outcome was never recorded.
                finish(instance, WorkflowInstanceStatus.FAILED, "Step " + step.getNodeType() + " did not finish", step.getErrorMessage());
                return Optional.empty();
            }

            step.start();
            step.setInputData(previousOutput);
            instance.setCurrentNode(step.getNode());
            return Optional.of(new NodeExecutionContext(
                    instance.getId(),
                    step.getId(),
                    step.getNodeType(),
                    step.getConfigSnapshot(),
                    previousOutput,
                    instance.getInputData(),
                    instance.getWorkflow().getName()));
        }

        instance.setOutputData(previousOutput);
        finish(instance, WorkflowInstanceStatus.COMPLETED, null, null);
        log.info("Execution {} completed", workflowInstanceId);
        return Optional.empty();
    }

    /**
     * Stores a step's output. An approval step instead waits for a decision: the step and the
     * execution are paused in the same transaction, so an approval can never arrive in between.
     *
     * @return true if the engine should continue with the next step
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordResult(Long nodeInstanceId, String output) {
        NodeInstance step = findStep(nodeInstanceId);
        WorkflowInstance instance = step.getWorkflowInstance();

        if (ApprovalNodeHandler.APPROVAL_SIGNAL.equals(output)) {
            step.setStatus(NodeInstanceStatus.WAITING_APPROVAL);
            approvalNodeHandler.approverEmail(step.getConfigSnapshot())
                    .flatMap(userRepository::findByEmail)
                    .ifPresent(step::setAssignedTo);
            if (instance.getStatus() == WorkflowInstanceStatus.RUNNING) {
                instance.setStatus(WorkflowInstanceStatus.PAUSED);
            }
            log.info("Execution {} paused for approval at step {}", instance.getId(), nodeInstanceId);
            return false;
        }

        step.markCompleted(output);
        return instance.getStatus() == WorkflowInstanceStatus.RUNNING;
    }

    /** Notes a failed attempt that is about to be retried. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRetry(Long nodeInstanceId, String error) {
        NodeInstance step = findStep(nodeInstanceId);
        step.setRetryCount(retryCount(step) + 1);
        step.setErrorMessage(error);
    }

    /** Marks the step FAILED and, unless it was already stopped, fails the execution. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Long nodeInstanceId, String error, String details) {
        NodeInstance step = findStep(nodeInstanceId);
        step.markFailed(error);
        WorkflowInstance instance = step.getWorkflowInstance();
        if (!instance.isFinished()) {
            finish(instance, WorkflowInstanceStatus.FAILED, "Failed at " + step.getNodeType() + ": " + error, details);
        }
    }

    /**
     * Puts a FAILED step back to PENDING so the execution can resume from it; returns the execution id. The step picks up
     * the node's current config when the node still exists, so a fixed config (e.g. a corrected
     * email address) is used by the retry.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long resetForRetry(Long nodeInstanceId) {
        NodeInstance step = findStep(nodeInstanceId);
        WorkflowInstance instance = step.getWorkflowInstance();

        if (step.getStatus() != NodeInstanceStatus.FAILED) {
            throw new IllegalStateException("Node is not in FAILED state");
        }
        if (instance.getStatus() != WorkflowInstanceStatus.FAILED) {
            throw new IllegalStateException("Only a failed execution can be retried");
        }

        if (step.getNode() != null) {
            step.snapshot(step.getNode());
        }
        step.setStatus(NodeInstanceStatus.PENDING);
        step.setRetryCount(retryCount(step) + 1);
        step.setErrorMessage(null);
        step.setStartedAt(null);
        step.setFinishedAt(null);
        step.setDurationMs(null);

        instance.setStatus(WorkflowInstanceStatus.PENDING);
        instance.setErrorMessage(null);
        instance.setErrorStackTrace(null);
        instance.setFinishedAt(null);
        instance.setDurationMs(null);
        log.info("Reset execution {} step {} for retry", instance.getId(), nodeInstanceId);
        return instance.getId();
    }

    /** Fails an execution after an unexpected engine error; a finished execution is left as is. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInstance(Long workflowInstanceId, String errorMessage, String stackTrace) {
        workflowInstanceRepository.findById(workflowInstanceId)
                .filter(instance -> !instance.isFinished())
                .ifPresent(instance -> finish(instance, WorkflowInstanceStatus.FAILED, errorMessage, stackTrace));
    }

    /**
     * After a restart, executions still marked RUNNING lost their worker mid-step. Whether that
     * step's side effect (an email, say) happened is unknown, so the step is failed rather than
     * re-run; the user can retry it explicitly.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failInterrupted(Long workflowInstanceId) {
        WorkflowInstance instance = workflowInstanceRepository.findById(workflowInstanceId).orElse(null);
        if (instance == null || instance.getStatus() != WorkflowInstanceStatus.RUNNING) {
            return;
        }
        String error = "Interrupted by a server restart";
        nodeInstanceRepository.findByWorkflowInstanceIdAndStatus(workflowInstanceId, NodeInstanceStatus.IN_PROGRESS)
                .forEach(step -> step.markFailed(error));
        finish(instance, WorkflowInstanceStatus.FAILED, error, null);
        log.warn("Execution {} was running during a restart and has been marked FAILED", workflowInstanceId);
    }

    private NodeInstance findStep(Long nodeInstanceId) {
        return nodeInstanceRepository.findById(nodeInstanceId)
                .orElseThrow(() -> new ResourceNotFoundException("Node instance not found"));
    }

    private static void finish(WorkflowInstance instance, WorkflowInstanceStatus status, String error, String details) {
        instance.setStatus(status);
        instance.setErrorMessage(error);
        instance.setErrorStackTrace(details);
        instance.complete();
    }

    private static int retryCount(NodeInstance step) {
        return step.getRetryCount() == null ? 0 : step.getRetryCount();
    }
}
