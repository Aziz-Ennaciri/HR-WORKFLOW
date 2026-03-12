package ma.rh.ai.hr_workflow.workflow.service.Impl;

import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import ma.rh.ai.hr_workflow.workflow.mappers.WorkflowMapper;
import ma.rh.ai.hr_workflow.workflow.mappers.WorkflowWithNodesMapper;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import ma.rh.ai.hr_workflow.workflow.service.IWorkflowService;

import org.springframework.data.domain.Page;


import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements IWorkflowService {

    private final WorkflowRepository workflowRepository;
    private final UserRepository userRepository;
    private final WorkflowMapper workflowMapper;
    private final WorkflowWithNodesMapper workflowWithNodesMapper;

    @Transactional
    @Override
    public WorkflowResponseDTO createWorkflow(CreateWorkflowDTO dto, Long creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        Workflow workflow = workflowMapper.toEntity(dto, creator);

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
        Workflow workflow = workflowRepository.findWorkflowWithNodes(id);
        if (workflow == null) {
        throw new RuntimeException("Workflow not found with id: " + id);
    }
        return workflowWithNodesMapper.toDTO(workflow,workflow.getNodes());
    }

    @Override
    public List<WorkflowResponseDTO> getAllWorkflows() {
        List<Workflow> workflows = workflowRepository.findAll();
        return workflows.stream()
                .map(workflowMapper::toResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<WorkflowResponseDTO> getAllWorkflowsPageable(Pageable pageable) {
        Page<Workflow> workflowsPage = workflowRepository.findByDeletedFalse(pageable);
        return workflowsPage.map(workflowMapper::toResponseDTO);
    }

    @Override
    public List<WorkflowResponseDTO> getWorkflowsByCreator(Long creatorId) {
        List<Workflow> workflowCreatedByUser = workflowRepository.findCreateById(creatorId);
        return workflowMapper.toResponseDTO(workflowCreatedByUser);
    }

    @Transactional
    @Override
    public WorkflowResponseDTO updateWorkflow(Long id, UpdateWorkflowDTO dto) {

        Workflow workflow = workflowRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Workflow not found"));

        workflow.setName(dto.getName());
        workflow.setDescription(dto.getDescription());
        workflow.setWorkflowKey(dto.getWorkflowKey());

        if (dto.getVersion() != null) {
            workflow.setVersion(dto.getVersion());
        }

        if (dto.getStatus() != null) {
            workflow.setStatus(dto.getStatus());
        }

        Workflow updated = workflowRepository.save(workflow);
        return workflowMapper.toResponseDTO(updated);
    }


    @Transactional
    @Override
    public WorkflowResponseDTO activateWorkflow(Long id) {
        Workflow workflow = workflowRepository.findById(id).orElseThrow(() -> new RuntimeException("Workflow not found"));
        if(workflow.getStatus() != WorkflowStatus.DRAFT){
            throw new IllegalStateException("Only DRAFT workflows can be activated");
        }
        if(workflow.getNodes().isEmpty()){
            throw new IllegalStateException("Workflow must have at least one node");
        }

        workflow.setStatus(WorkflowStatus.ACTIVE);
        return workflowMapper.toResponseDTO(workflow);
    }

    @Override
    public void deleteWorkflow(Long id) {
        Workflow workflow = workflowRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Workflow not found"));

        workflow.setDeleted(true);
        workflowRepository.save(workflow);
    }

    @Transactional
    @Override
    public WorkflowResponseDTO archiveWorkflow(Long id) {
        Workflow workflow = workflowRepository.findById(id).orElseThrow(() -> new RuntimeException("Workflow not found"));

        if (workflow.getStatus() == WorkflowStatus.ACTIVE) {
            throw new IllegalStateException("the workflow still activated , you should deactivate if you wanna archive it");
        }

        workflow.setStatus(WorkflowStatus.ARCHIVED);
        workflow.setUpdatedAt(LocalDateTime.now());
        return workflowMapper.toResponseDTO(workflow);
    }
}
