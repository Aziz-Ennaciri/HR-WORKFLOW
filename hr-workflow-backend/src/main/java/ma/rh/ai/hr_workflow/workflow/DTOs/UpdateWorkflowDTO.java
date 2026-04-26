package ma.rh.ai.hr_workflow.workflow.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateWorkflowDTO {
    @NotBlank
    private String name;

    private String description;

    @NotBlank
    private String workflowKey;

    private Integer version;

    private WorkflowStatus status;

    private Long createdById;

    private String edgesJson;
}
