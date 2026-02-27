package ma.rh.ai.hr_workflow.execution.DTOs;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowInstanceDetailDTO {

    private WorkflowInstanceResponseDTO workflowInstance;
    private List<NodeInstanceResponseDTO> nodeInstances;
}