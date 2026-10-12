package ma.rh.ai.hr_workflow.workflow.service;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.PatchWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;

import java.util.List;

public interface IWorkflowService {

    WorkflowResponseDTO createWorkflow(CreateWorkflowDTO dto, String creatorEmail);

    WorkflowResponseDTO getWorkflowById(Long id);

    List<WorkflowResponseDTO> getAllWorkflows();

    List<WorkflowResponseDTO> getWorkflowsByCreatorEmail(String email);

    WorkflowResponseDTO updateWorkflow(Long id, UpdateWorkflowDTO dto);

    WorkflowResponseDTO patchWorkflow(Long id, PatchWorkflowDTO dto);

    WorkflowResponseDTO activateWorkflow(Long id);

    void deleteWorkflow(Long id);

    WorkflowResponseDTO archiveWorkflow(Long id);

    WorkflowResponseDTO duplicateWorkflow(Long workflowId, String currentUserEmail);
}
