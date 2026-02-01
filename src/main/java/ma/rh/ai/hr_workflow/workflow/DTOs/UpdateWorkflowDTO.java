package ma.rh.ai.hr_workflow.workflow.DTOs;

import jakarta.validation.constraints.NotBlank;

public class UpdateWorkflowDTO {
    @NotBlank
    private String name;

    private String description;
}
