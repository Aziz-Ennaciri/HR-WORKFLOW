package ma.rh.ai.hr_workflow.execution.service.Impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowInstanceService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;

@Service
@RequiredArgsConstructor
public class WorkflowInstanceServiceImpl implements IWorkflowInstanceService{
    private final WorkflowInstancerepository workflowInstancerepository;
    private final WorkflowRepository workflowRepository;

    @Override
    public WorkflowInstance createInstance(TriggerWorkflowInstanceDTO dto, User user) {
        Workflow workflow = workflowRepository.findById(dto.getWorkflowId()).orElseThrow(() -> new RuntimeException("Workflow not found") );

        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setWorkflow(workflow);
        workflowInstance.setTriggeredBy(user);
        workflowInstance.setStatus(WorkflowInstanceStatus.PENDING);
        workflowInstance.setInputData(dto.getInputData());
        workflowInstance.setCreatedAt(LocalDateTime.now());

        WorkflowInstance savedWorkflowInstance = workflowInstancerepository.save(workflowInstance);
        return savedWorkflowInstance;
    }

    @Override
    public WorkflowInstance startInstance(Long workflowInstanceId) {
        WorkflowInstance workflowInstance = workflowInstancerepository.findById(workflowInstanceId).orElseThrow(()->new RuntimeException("WorkflowInstance not found"));
        workflowInstance.start();
        return workflowInstance ;
    }

    @Override
    public WorkflowInstance completeInstance(Long workflowInstanceId, String outputData) {
        WorkflowInstance workflowInstance = workflowInstancerepository.findById(workflowInstanceId).orElseThrow(()->new RuntimeException("WorkflowInstance not found"));
        workflowInstance.setStatus(WorkflowInstanceStatus.COMPLETED);
        workflowInstance.setOutputData(outputData);
        workflowInstance.complete();
        return workflowInstance;
    }

    @Override
    public WorkflowInstance failInstance(Long workflowInstanceId, String errorMessage, String stackTrace) {
        WorkflowInstance workflowInstance = workflowInstancerepository.findById(workflowInstanceId).orElseThrow(()->new RuntimeException("WorkflowInstance not found"));
        workflowInstance.setStatus(WorkflowInstanceStatus.FAILED);
        workflowInstance.setErrorMessage(errorMessage);
        workflowInstance.setErrorStackTrace(stackTrace);
        workflowInstance.complete();
        return workflowInstance;
    }
    
}
