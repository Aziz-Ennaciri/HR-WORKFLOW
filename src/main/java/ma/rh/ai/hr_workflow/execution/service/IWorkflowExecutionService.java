package ma.rh.ai.hr_workflow.execution.service;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.user.model.User;

public interface IWorkflowExecutionService {
    WorkflowInstance triggerWorkflow(TriggerWorkflowInstanceDTO dto, User user);

    void continueExecution(Long workflowInstanceId);
}
