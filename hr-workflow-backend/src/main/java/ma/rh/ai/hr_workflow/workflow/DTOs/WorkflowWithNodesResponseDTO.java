package ma.rh.ai.hr_workflow.workflow.DTOs;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowWithNodesResponseDTO {
    private WorkflowResponseDTO workflow;
    private List<NodeResponseDTO> nodes;
}
