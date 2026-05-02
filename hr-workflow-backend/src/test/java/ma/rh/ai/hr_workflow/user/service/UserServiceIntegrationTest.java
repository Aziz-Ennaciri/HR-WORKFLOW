package ma.rh.ai.hr_workflow.user.service;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.DTOs.CreateUserDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginRequestDTO;
import ma.rh.ai.hr_workflow.user.DTOs.LoginResponseDTO;
import ma.rh.ai.hr_workflow.user.DTOs.UserResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("UserService Integration Tests")
class UserServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IUserService userService;

    private CreateUserDTO buildCreateDTO(String email, String password, String role) {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setFirstName("Test");
        dto.setLastName("User");
        dto.setRole(role);
        return dto;
    }

    private LoginRequestDTO buildLoginDTO(String email, String password) {
        LoginRequestDTO dto = new LoginRequestDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        return dto;
    }

    @Nested
    @DisplayName("register → login flow")
    class RegisterThenLogin {

        @Test
        @DisplayName("register a new user then login returns a JWT token")
        void register_then_login_returns_token() {
            // Arrange
            CreateUserDTO createDTO = buildCreateDTO("newuser@test.com", "secret123", "ROLE_RH");

            // Act
            UserResponseDTO registered = userService.register(createDTO);
            LoginResponseDTO loginResponse = userService.login(buildLoginDTO("newuser@test.com", "secret123"));

            // Assert
            assertThat(registered.getEmail()).isEqualTo("newuser@test.com");
            assertThat(loginResponse.getToken()).isNotBlank();
            assertThat(loginResponse.getUser().getEmail()).isEqualTo("newuser@test.com");
        }
    }

    @Nested
    @DisplayName("register — duplicate email fails")
    class DuplicateEmail {

        @Test
        @DisplayName("registering with an already-used email throws RuntimeException")
        void register_duplicate_email_throws() {
            // Arrange
            userService.register(buildCreateDTO("dup@test.com", "pass", "ROLE_RH"));

            // Act & Assert
            assertThatThrownBy(() ->
                    userService.register(buildCreateDTO("dup@test.com", "pass2", "ROLE_RH")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Email already exists");
        }
    }

    @Nested
    @DisplayName("login — wrong password fails")
    class WrongPassword {

        @Test
        @DisplayName("login with incorrect password throws RuntimeException")
        void login_wrong_password_throws() {
            // Arrange
            userService.register(buildCreateDTO("secure@test.com", "correctPass", "ROLE_RH"));

            // Act & Assert
            assertThatThrownBy(() ->
                    userService.login(buildLoginDTO("secure@test.com", "wrongPass")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Invalid email or password");
        }
    }

    @Nested
    @DisplayName("disable user → login fails")
    class DisableUser {

        @Test
        @DisplayName("disabled user cannot log in")
        void disabled_user_login_throws() {
            // Arrange
            UserResponseDTO user = userService.register(buildCreateDTO("dis@test.com", "pass", "ROLE_RH"));
            userService.disableUser(user.getId());

            // Act & Assert
            assertThatThrownBy(() ->
                    userService.login(buildLoginDTO("dis@test.com", "pass")))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("disabled");
        }
    }
}
