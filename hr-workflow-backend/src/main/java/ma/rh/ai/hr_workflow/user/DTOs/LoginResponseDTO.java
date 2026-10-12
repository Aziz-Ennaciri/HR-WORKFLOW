package ma.rh.ai.hr_workflow.user.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponseDTO {
    /** Short-lived access token (JWT) for the Authorization header. */
    private String token;
    /** Single-use token to obtain a new access token at {@code /users/refresh}. */
    private String refreshToken;
    /** Access token lifetime in seconds. */
    private long expiresIn;
    private UserResponseDTO user;
}
