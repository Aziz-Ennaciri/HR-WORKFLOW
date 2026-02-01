package ma.rh.ai.hr_workflow.workflow.service;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import org.springframework.data.domain.Page;


import java.awt.print.Pageable;
import java.util.List;

public interface IWorkflow {
    WorkflowResponseDTO createWorkflow(CreateWorkflowDTO dto, Long creatorId);

    WorkflowResponseDTO getWorkflowById(Long id);

    WorkflowWithNodesResponseDTO getWorkflowWithNodes(Long id);

    List<WorkflowResponseDTO> getAllWorkflows();

    Page<WorkflowResponseDTO> getAllWorkflows(Pageable pageable);

    List<WorkflowResponseDTO> getWorkflowsByCreator(Long creatorId);

    List<WorkflowResponseDTO> getWorkflowsByStatus(String status);

    WorkflowResponseDTO updateWorkflow(Long id, UpdateWorkflowDTO dto);

    WorkflowResponseDTO publishWorkflow(Long id);

    void deleteWorkflow(Long id);

    WorkflowResponseDTO archiveWorkflow(Long id);

    boolean existsById(Long id);
}
