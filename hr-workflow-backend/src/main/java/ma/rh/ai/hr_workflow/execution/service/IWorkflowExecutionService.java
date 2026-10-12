package ma.rh.ai.hr_workflow.execution.service;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.user.model.User;

public interface IWorkflowExecutionService {
    WorkflowInstance triggerWorkflow(TriggerWorkflowInstanceDTO dto, User user);

    /** Queues the execution to run; a no-op if it is already running, paused for approval, or finished. */
    void continueExecution(Long workflowInstanceId);

    /** Queues a paused execution to resume once its approval step has been approved. */
    void resumeAfterApproval(Long workflowInstanceId);

    void retryFromNode(Long executionId, Long nodeId);

    /** Resets a FAILED step to PENDING and resumes its execution from that step. */
    void retryStep(Long nodeInstanceId);
}
