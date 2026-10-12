package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.config.ExecutionConfig;
import ma.rh.ai.hr_workflow.execution.config.ExecutionProperties;
import ma.rh.ai.hr_workflow.execution.handler.NodeExecutionContext;
import ma.rh.ai.hr_workflow.execution.handler.NodeHandler;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowExecutionService;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowInstanceService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;

/**
 * Runs executions on a background pool. An execution's steps are frozen when it is triggered;
 * a worker first {@link ExecutionTransactionHelper#claim claims} the execution (only one worker
 * can win), then runs its steps one at a time until it completes, fails, is cancelled, or
 * pauses for an approval.
 */
@Service
@Slf4j
public class WorkflowExecutionServiceImpl implements IWorkflowExecutionService {

    private final IWorkflowInstanceService workflowInstanceService;
    private final WorkflowRepository workflowRepository;
    private final NodeRepository nodeRepository;
    private final NodeInstanceRepository nodeInstanceRepository;
    private final ExecutionTransactionHelper txHelper;
    private final TaskExecutor executor;
    private final ExecutionProperties.Retry retry;
    private final Map<NodeType, NodeHandler> handlers = new EnumMap<>(NodeType.class);

    public WorkflowExecutionServiceImpl(IWorkflowInstanceService workflowInstanceService,
                                        WorkflowRepository workflowRepository,
                                        NodeRepository nodeRepository,
                                        NodeInstanceRepository nodeInstanceRepository,
                                        ExecutionTransactionHelper txHelper,
                                        List<NodeHandler> nodeHandlers,
                                        @Qualifier(ExecutionConfig.EXECUTOR) TaskExecutor executor,
                                        ExecutionProperties properties) {
        this.workflowInstanceService = workflowInstanceService;
        this.workflowRepository = workflowRepository;
        this.nodeRepository = nodeRepository;
        this.nodeInstanceRepository = nodeInstanceRepository;
        this.txHelper = txHelper;
        this.executor = executor;
        this.retry = properties.retry();
        nodeHandlers.forEach(handler -> handlers.put(handler.getType(), handler));
        log.info("Registered node handlers for {}", handlers.keySet());
    }

    @Override
    @Transactional
    public WorkflowInstance triggerWorkflow(TriggerWorkflowInstanceDTO dto, User user) {
        Workflow workflow = validateWorkflow(dto.getWorkflowId());
        List<Node> nodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflow.getId());
        if (nodes.isEmpty()) {
            throw new IllegalStateException("Workflow has no nodes");
        }

        WorkflowInstance workflowInstance = workflowInstanceService.createInstance(dto, user);
        for (Node node : nodes) {
            NodeInstance step = new NodeInstance();
            step.setWorkflowInstance(workflowInstance);
            step.snapshot(node);
            step.setExecutionOrder(node.getOrderIndex());
            step.setStatus(NodeInstanceStatus.PENDING);
            nodeInstanceRepository.save(step);
        }

        submitAfterCommit(workflowInstance.getId());
        log.info("Execution {} triggered for workflow {} by {}", workflowInstance.getId(), workflow.getId(), user.getEmail());
        return workflowInstance;
    }

    /** Queues the execution to (re)start; a no-op if it is already running or finished. */
    @Override
    public void continueExecution(Long workflowInstanceId) {
        submit(workflowInstanceId);
    }

    @Override
    public void resumeAfterApproval(Long workflowInstanceId) {
        submit(workflowInstanceId);
    }

    @Override
    public void retryFromNode(Long executionId, Long nodeId) {
        NodeInstance step = nodeInstanceRepository.findByWorkflowInstanceIdAndNodeId(executionId, nodeId)
                .orElseThrow(() -> new ResourceNotFoundException("Node instance not found"));
        retryStep(step.getId());
    }

    @Override
    public void retryStep(Long nodeInstanceId) {
        submit(txHelper.resetForRetry(nodeInstanceId));
    }

    /**
     * Runs an execution on the calling thread until it stops. Normally called by a pool worker;
     * returns immediately if another worker already owns the execution.
     */
    public void runExecution(Long workflowInstanceId) {
        MDC.put("executionId", String.valueOf(workflowInstanceId));
        try {
            if (!claim(workflowInstanceId)) {
                return;
            }
            Optional<NodeExecutionContext> next;
            while ((next = txHelper.nextStep(workflowInstanceId)).isPresent()) {
                if (!runStep(next.get())) {
                    return;
                }
            }
        } catch (Exception e) {
            log.error("Execution {} failed unexpectedly", workflowInstanceId, e);
            try {
                txHelper.failInstance(workflowInstanceId, "Error: " + e.getMessage(), stackTrace(e));
            } catch (Exception markFailed) {
                log.error("Could not mark execution {} as failed", workflowInstanceId, markFailed);
            }
        } finally {
            MDC.remove("executionId");
        }
    }

    public void submit(Long workflowInstanceId) {
        try {
            executor.execute(() -> runExecution(workflowInstanceId));
        } catch (TaskRejectedException e) {
            // The execution stays PENDING/PAUSED in the database; the sweeper submits it again later.
            log.warn("Executor is saturated; execution {} will be picked up by the sweeper", workflowInstanceId);
        }
    }

    private void submitAfterCommit(Long workflowInstanceId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    submit(workflowInstanceId);
                }
            });
        } else {
            submit(workflowInstanceId);
        }
    }

    private boolean claim(Long workflowInstanceId) {
        try {
            return txHelper.claim(workflowInstanceId);
        } catch (ObjectOptimisticLockingFailureException e) {
            log.debug("Execution {} was claimed by another worker", workflowInstanceId);
            return false;
        }
    }

    /** Runs one step and records its outcome; returns true if the execution should go on. */
    private boolean runStep(NodeExecutionContext step) {
        NodeHandler handler = handlers.get(step.nodeType());
        if (handler == null) {
            txHelper.recordFailure(step.nodeInstanceId(), "No handler registered for node type " + step.nodeType(), null);
            return false;
        }

        long backoffMillis = retry.initialBackoff().toMillis();
        for (int attempt = 1; ; attempt++) {
            try {
                String output = handler.execute(step);
                return txHelper.recordResult(step.nodeInstanceId(), output);
            } catch (Exception e) {
                boolean willRetry = attempt < retry.maxAttempts() && isTransient(e);
                log.warn("Step {} ({}) attempt {}/{} failed: {}{}", step.nodeInstanceId(), step.nodeType(),
                        attempt, retry.maxAttempts(), e.getMessage(), willRetry ? " — retrying" : "");
                if (!willRetry) {
                    txHelper.recordFailure(step.nodeInstanceId(), e.getMessage(), stackTrace(e));
                    return false;
                }
                txHelper.recordRetry(step.nodeInstanceId(), e.getMessage());
                if (!sleep(backoffMillis)) {
                    txHelper.recordFailure(step.nodeInstanceId(), "Interrupted while waiting to retry", null);
                    return false;
                }
                backoffMillis = (long) (backoffMillis * retry.multiplier());
            }
        }
    }

    /**
     * Only network-level failures and 5xx/429 responses are retried. Anything else (bad config,
     * 4xx, a mail error after a partial send) would fail again or risk duplicate side effects.
     */
    static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ResourceAccessException
                    || t instanceof SocketTimeoutException
                    || t instanceof ConnectException
                    || t instanceof HttpServerErrorException
                    || t instanceof HttpClientErrorException.TooManyRequests) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Workflow validateWorkflow(Long workflowId) {
        Workflow workflow = workflowRepository.findById(workflowId)
                .filter(w -> !w.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Workflow not found"));
        if (workflow.getStatus() != WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("Workflow is not active");
        }
        return workflow;
    }

    private static String stackTrace(Throwable e) {
        StringWriter out = new StringWriter();
        e.printStackTrace(new PrintWriter(out));
        String trace = out.toString();
        return trace.length() > 4000 ? trace.substring(0, 4000) : trace;
    }
}
