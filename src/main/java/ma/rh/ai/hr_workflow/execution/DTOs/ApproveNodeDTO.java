package ma.rh.ai.hr_workflow.execution.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApproveNodeDTO {

    @NotBlank(message = "Actor is required")
    private String actor;

    private String comment;
}