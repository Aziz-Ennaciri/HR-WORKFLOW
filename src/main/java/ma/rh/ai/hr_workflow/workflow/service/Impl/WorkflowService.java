package ma.rh.ai.hr_workflow.workflow.service.Impl;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import ma.rh.ai.hr_workflow.workflow.service.IWorkflow;
import org.springframework.data.domain.Page;


import java.awt.print.Pageable;
import java.util.List;

public class WorkflowService implements IWorkflow {
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
    public List<WorkflowResponseDTO> getWorkflowsByStatus(String status) {
        return List.of();
    }

    @Override
    public WorkflowResponseDTO updateWorkflow(Long id, UpdateWorkflowDTO dto) {
        return null;
    }

    @Override
    public WorkflowResponseDTO publishWorkflow(Long id) {
        return null;
    }

    @Override
    public void deleteWorkflow(Long id) {

    }

    @Override
    public WorkflowResponseDTO archiveWorkflow(Long id) {
        return null;
    }

    @Override
    public boolean existsById(Long id) {
        return false;
    }
}
