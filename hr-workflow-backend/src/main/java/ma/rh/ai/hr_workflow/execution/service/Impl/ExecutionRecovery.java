package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.time.LocalDateTime;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.execution.config.ExecutionProperties;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;

/**
 * Keeps executions moving when the in-memory queue loses them: on startup it fails executions
 * that were mid-step when the server stopped, and periodically re-submits executions that were
 * never started or whose approval resume was lost. Claiming is idempotent, so re-submitting an
 * execution that is already running is harmless. Assumes a single backend instance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ExecutionRecovery {

    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final ExecutionTransactionHelper txHelper;
    private final WorkflowExecutionServiceImpl executionService;
    private final ExecutionProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAfterRestart() {
        if (!properties.sweeper().enabled()) {
            return;
        }
        var interrupted = workflowInstanceRepository.findIdsByStatus(WorkflowInstanceStatus.RUNNING);
        interrupted.forEach(txHelper::failInterrupted);
        if (!interrupted.isEmpty()) {
            log.warn("Marked {} interrupted execution(s) as FAILED after restart", interrupted.size());
        }
        sweep();
    }

    @Scheduled(fixedDelayString = "${execution.sweeper.interval:PT30S}", initialDelayString = "${execution.sweeper.interval:PT30S}")
    public void sweep() {
        if (!properties.sweeper().enabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.sweeper().pendingGrace());
        workflowInstanceRepository.findStalePendingIds(cutoff).forEach(executionService::submit);
        workflowInstanceRepository.findPausedIdsWithoutPendingApproval().forEach(executionService::submit);
    }
}
