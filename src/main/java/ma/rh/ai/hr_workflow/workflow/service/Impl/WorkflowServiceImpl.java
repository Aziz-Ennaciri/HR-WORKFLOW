package ma.rh.ai.hr_workflow.workflow.service.Impl;

import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import ma.rh.ai.hr_workflow.workflow.mappers.WorkflowMapper;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import ma.rh.ai.hr_workflow.workflow.service.IWorkflowService;

import org.springframework.data.domain.Page;


import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements IWorkflowService {

    private final WorkflowRepository workflowRepository;
    private final UserRepository userRepository;
    private final WorkflowMapper workflowMapper;

    @Override
    public WorkflowResponseDTO createWorkflow(CreateWorkflowDTO dto, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Workflow workflow = new Workflow();
        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        workflow.setCreatedBy(creator);
        workflow.setStatus(WorkflowStatus.DRAFT);
        workflow.setVersion(1);
        workflow.setCreatedAt(LocalDateTime.now());

        Workflow savedWorkflow = workflowRepository.save(workflow);

        return workflowMapper.toResponseDTO(savedWorkflow);
    }

    @Override
    public WorkflowResponseDTO getWorkflowById(Long id) {
        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));
        return workflowMapper.toResponseDTO(workflow);
    }

    @Override
    public WorkflowWithNodesResponseDTO getWorkflowWithNodes(Long id) {
        return null;
    }

    @Override
    public List<WorkflowResponseDTO> getAllWorkflows() {
        List<Workflow> workflows = workflowRepository.findByDeletedFalse();
        if(workflows.isEmpty()){
            throw new RuntimeException("Workflow not found");
        }
        /* return workflows.stream().map(workflowMapper::toResponseDTO).toList(); */
        return workflowMapper.toResponseDTO(workflows);
    }

    @Override
    public Page<WorkflowResponseDTO> getAllWorkflows(Pageable pageable) {
        Page<Workflow> workflowsPage = workflowRepository.findByDeletedFalse(pageable);
        return workflowsPage.map(workflowMapper::toResponseDTO);
    }

    @Override
    public List<WorkflowResponseDTO> getWorkflowsByCreator(Long creatorId) {
        return List.of();
    }

    @Override
    public WorkflowResponseDTO updateWorkflow(Long id, UpdateWorkflowDTO dto) {
        Workflow workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Workflow not found"));
        
        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        return null;
    }

    @Override
    public WorkflowResponseDTO activateWorkflow(Long id) {
        return null;
    }

    @Override
    public void deleteWorkflow(Long id) {
        Workflow workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Workflow not found"));

        workflow.setDeleted(true);
        workflowRepository.save(workflow);
    }

    @Override
    public WorkflowResponseDTO archiveWorkflow(Long id) {
        return null;
    }
}
