package ma.rh.ai.hr_workflow.user.controllers;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.DTOs.CreateUserDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("UserController Integration Tests")
class UserControllerIntegrationTest extends AbstractIntegrationTest {

    private CreateUserDTO buildDTO(String email, String password, String role) {
        CreateUserDTO dto = new CreateUserDTO();
        dto.setEmail(email);
        dto.setPassword(password);
        dto.setFirstName("Test");
        dto.setLastName("User");
        dto.setRole(role);
        return dto;
    }

    @Nested
    @DisplayName("POST /api/v1/users/register")
    class Register {

        @Test
        @DisplayName("register a new user returns 201 with user body")
        void register_returns_201() throws Exception {
            // Arrange
            String body = objectMapper.writeValueAsString(buildDTO("reg@test.com", "pass123", "ROLE_RH"));

            // Act & Assert
            mockMvc.perform(post("/api/v1/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("reg@test.com"));
        }

        @Test
        @DisplayName("registering with a duplicate email returns 500 (unhandled)")
        void register_duplicate_email_returns_error() throws Exception {
            // Arrange — first registration (within the same test transaction)
            String body = objectMapper.writeValueAsString(buildDTO("dup@test.com", "pass", "ROLE_RH"));
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());

            // Act — second registration with same email
            mockMvc.perform(post("/api/v1/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/login")
    class Login {

        @Test
        @DisplayName("login with correct credentials returns 200 and a JWT token")
        void login_valid_credentials_returns_token() throws Exception {
            // Arrange — register first
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("login@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());

            String loginBody = "{\"email\":\"login@test.com\",\"password\":\"pass\"}";

            // Act & Assert
            mockMvc.perform(post("/api/v1/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("login@test.com"));
        }

        @Test
        @DisplayName("login with wrong password returns 500 (unhandled exception)")
        void login_wrong_password_returns_error() throws Exception {
            // Arrange
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("badpwd@test.com", "correct", "ROLE_RH"))))
                    .andExpect(status().isCreated());

            String loginBody = "{\"email\":\"badpwd@test.com\",\"password\":\"wrong\"}";

            // Act & Assert
            mockMvc.perform(post("/api/v1/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users")
    class GetAllUsers {

        @Test
        @DisplayName("authenticated user can list all users")
        void getAllUsers_authenticated_returns_200() throws Exception {
            // Arrange — register and get token
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("list@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());

            String token = getAuthToken("list@test.com", "pass");

            // Act & Assert
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("unauthenticated request returns 403 (security filter)")
        void getAllUsers_unauthenticated_returns_403() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/{id} — disable user")
    class DisableUser {

        @Test
        @DisplayName("authenticated user can disable another user — returns 204")
        void disableUser_returns_204() throws Exception {
            // Arrange — register two users
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("admin-dis@test.com", "pass", "ROLE_ADMIN"))))
                    .andExpect(status().isCreated());

            String registerResponse = mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("target-dis@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long targetId = objectMapper.readTree(registerResponse).get("id").asLong();
            String token = getAuthToken("admin-dis@test.com", "pass");

            // Act & Assert
            mockMvc.perform(delete("/api/v1/users/" + targetId)
                            .header("Authorization", token))
                    .andExpect(status().isNoContent());
        }
    }
}
