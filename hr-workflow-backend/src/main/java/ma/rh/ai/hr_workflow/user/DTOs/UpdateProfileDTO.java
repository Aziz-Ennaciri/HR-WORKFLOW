package ma.rh.ai.hr_workflow.user.DTOs;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileDTO {
    @NotBlank
    private String firstName;
    @NotBlank
    private String lastName;
}
