package ma.rh.ai.hr_workflow.workflow.service.Impl;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import org.springframework.data.domain.Page;


import org.springframework.data.domain.Pageable;
import java.util.List;

public class WorkflowServiceImpl implements ma.rh.ai.hr_workflow.workflow.service.WorkflowService {
    @Override
    public WorkflowResponseDTO createWorkflow(CreateWorkflowDTO dto, Long creatorId) {
        return null;
    }

    @Override
    public WorkflowResponseDTO getWorkflowById(Long id) {
        return null;
    }

    @Override
    public WorkflowWithNodesResponseDTO getWorkflowWithNodes(Long id) {
        return null;
    }

    @Override
    public List<WorkflowResponseDTO> getAllWorkflows() {
        return List.of();
    }

    @Override
    public Page<WorkflowResponseDTO> getAllWorkflows(Pageable pageable) {
        return null;
    }

    @Override
    public List<WorkflowResponseDTO> getWorkflowsByCreator(Long creatorId) {
        return List.of();
    }

    @Override
    public List<WorkflowResponseDTO> getWorkflowsByStatus(WorkflowStatus status) {
        return List.of();
    }

    @Override
    public WorkflowResponseDTO updateWorkflow(Long id, UpdateWorkflowDTO dto) {
        return null;
    }

    @Override
    public WorkflowResponseDTO activateWorkflow(Long id) {
        return null;
    }

    @Override
    public void deleteWorkflow(Long id) {

    }

    @Override
    public WorkflowResponseDTO archiveWorkflow(Long id) {
        return null;
    }
}
