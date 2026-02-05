package ma.rh.ai.hr_workflow.workflow.DTOs;

import java.util.List;

import lombok.Data;

@Data
public class WorkflowWithNodesResponseDTO {
    private WorkflowResponseDTO workflow;
    private List<NodeResponseDTO> nodes;
}
