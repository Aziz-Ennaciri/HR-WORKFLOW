package ma.rh.ai.hr_workflow.execution.service.Impl;

import org.springframework.stereotype.Service;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowExecutionService;
import ma.rh.ai.hr_workflow.user.model.User;

@Service
public class WorkflowExecutionServiceImpl implements IWorkflowExecutionService{

    @Override
    public WorkflowInstance triggerWorkflow(TriggerWorkflowInstanceDTO dto, User user) {
        throw new UnsupportedOperationException("Unimplemented method 'triggerWorkflow'");
    }

    @Override
    public void continueExecution(Long workflowInstanceId) {
        throw new UnsupportedOperationException("Unimplemented method 'continueExecution'");
    }
    
}
