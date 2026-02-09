package ma.rh.ai.hr_workflow.execution.service;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.user.model.User;

public interface IWorkflowInstanceService {
   WorkflowInstance createInstance(TriggerWorkflowInstanceDTO dto, User user);

    WorkflowInstance startInstance(Long workflowInstanceId);

    WorkflowInstance completeInstance(Long workflowInstanceId, String outputData);

    WorkflowInstance failInstance(Long workflowInstanceId, String errorMessage, String stackTrace);
}