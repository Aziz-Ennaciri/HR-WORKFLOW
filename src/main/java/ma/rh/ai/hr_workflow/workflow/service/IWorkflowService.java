package ma.rh.ai.hr_workflow.workflow.service;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import org.springframework.data.domain.Page;


import org.springframework.data.domain.Pageable;
import java.util.List;

public interface IWorkflowService {
    WorkflowResponseDTO createWorkflow(CreateWorkflowDTO dto, Long creatorId);

    WorkflowResponseDTO getWorkflowById(Long id);

    WorkflowWithNodesResponseDTO getWorkflowWithNodes(Long id);


    Page<WorkflowResponseDTO> getAllWorkflows(Pageable pageable);

    List<WorkflowResponseDTO> getWorkflowsByCreator(Long creatorId);

    WorkflowResponseDTO updateWorkflow(Long id, UpdateWorkflowDTO dto);

    WorkflowResponseDTO activateWorkflow(Long id);

    void deleteWorkflow(Long id);

    WorkflowResponseDTO archiveWorkflow(Long id);
}
