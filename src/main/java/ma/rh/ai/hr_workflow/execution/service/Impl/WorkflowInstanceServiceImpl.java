package ma.rh.ai.hr_workflow.execution.service.Impl;

import org.springframework.stereotype.Service;

import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowInstanceService;
import ma.rh.ai.hr_workflow.user.model.User;

@Service
public class WorkflowInstanceServiceImpl implements IWorkflowInstanceService{

    @Override
    public WorkflowInstance createInstance(TriggerWorkflowInstanceDTO dto, User user) {
        throw new UnsupportedOperationException("Unimplemented method 'createInstance'");
    }

    @Override
    public WorkflowInstance startInstance(Long workflowInstanceId) {
        throw new UnsupportedOperationException("Unimplemented method 'startInstance'");
    }

    @Override
    public WorkflowInstance completeInstance(Long workflowInstanceId, String outputData) {
        throw new UnsupportedOperationException("Unimplemented method 'completeInstance'");
    }

    @Override
    public WorkflowInstance failInstance(Long workflowInstanceId, String errorMessage, String stackTrace) {
        throw new UnsupportedOperationException("Unimplemented method 'failInstance'");
    }
    
}
