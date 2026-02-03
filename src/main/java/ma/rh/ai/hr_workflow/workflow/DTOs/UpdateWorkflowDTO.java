package ma.rh.ai.hr_workflow.workflow.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateWorkflowDTO {
    @NotBlank
    private String name;

    private String description;
}
