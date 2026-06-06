package ma.rh.ai.hr_workflow.user.DTOs;

import java.time.LocalDateTime;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseDTO {
    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private Set<String> roles;
    private String theme;
    private String language;
    private boolean emailNotificationsEnabled;
    private LocalDateTime lastLoginAt;
}
