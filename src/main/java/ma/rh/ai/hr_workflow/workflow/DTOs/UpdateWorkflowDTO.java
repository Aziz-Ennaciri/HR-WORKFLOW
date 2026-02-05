package ma.rh.ai.hr_workflow.workflow.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateWorkflowDTO {
    @NotBlank
    private String name;

    private String description;
}
