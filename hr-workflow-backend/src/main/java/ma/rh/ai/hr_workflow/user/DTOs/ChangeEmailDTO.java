package ma.rh.ai.hr_workflow.user.DTOs;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChangeEmailDTO {
    @NotBlank
    @Email
    private String newEmail;
    @NotBlank
    private String currentPassword;
}
