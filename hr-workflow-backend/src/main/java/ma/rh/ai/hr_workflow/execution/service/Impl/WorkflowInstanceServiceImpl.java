package ma.rh.ai.hr_workflow.execution.service.Impl;

import ma.rh.ai.hr_workflow.exceptions.ResourceNotFoundException;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;
import ma.rh.ai.hr_workflow.execution.service.IWorkflowInstanceService;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;

@Service
@RequiredArgsConstructor
public class WorkflowInstanceServiceImpl implements IWorkflowInstanceService{
    private final WorkflowInstanceRepository workflowInstanceRepository;
    private final WorkflowRepository workflowRepository;

    @Override
    @Transactional
    public WorkflowInstance createInstance(TriggerWorkflowInstanceDTO dto, User user) {
        Workflow workflow = workflowRepository.findById(dto.getWorkflowId()).orElseThrow(() -> new ResourceNotFoundException("Workflow not found") );

        WorkflowInstance workflowInstance = new WorkflowInstance();
        workflowInstance.setWorkflow(workflow);
        workflowInstance.setTriggeredBy(user);
        workflowInstance.setStatus(WorkflowInstanceStatus.PENDING);
        workflowInstance.setInputData(dto.getInputData());
        workflowInstance.setCreatedAt(LocalDateTime.now());

        WorkflowInstance savedWorkflowInstance = workflowInstanceRepository.save(workflowInstance);
        return savedWorkflowInstance;
    }
}
