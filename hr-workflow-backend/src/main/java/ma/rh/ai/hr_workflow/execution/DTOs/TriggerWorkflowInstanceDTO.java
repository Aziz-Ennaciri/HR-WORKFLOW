package ma.rh.ai.hr_workflow.execution.DTOs;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TriggerWorkflowInstanceDTO {

    @NotNull(message = "Workflow ID is required")
    private Long workflowId;

    private String inputData;
}