package ma.rh.ai.hr_workflow.workflow.service;

import ma.rh.ai.hr_workflow.workflow.DTOs.SaveWorkflowGraphDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;

public interface IWorkflowGraphService {

    /** Replaces the workflow's nodes, connections and layout with the editor's canvas, atomically. */
    WorkflowResponseDTO saveGraph(Long workflowId, SaveWorkflowGraphDTO dto);
}
