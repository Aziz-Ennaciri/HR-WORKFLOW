package ma.rh.ai.hr_workflow.user.controllers;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.DTOs.CreateUserDTO;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
             
            String body = objectMapper.writeValueAsString(buildDTO("reg@test.com", "pass123", "ROLE_RH"));

            mockMvc.perform(post("/api/v1/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.email").value("reg@test.com"));
        }

        @Test
        @DisplayName("registering with a duplicate email returns 409")
        void register_duplicate_email_returns_error() throws Exception {
            String body = objectMapper.writeValueAsString(buildDTO("dup@test.com", "pass", "ROLE_RH"));
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("anonymous self-registration cannot grant ROLE_ADMIN")
        void register_anonymous_cannotBecomeAdmin() throws Exception {
            String body = objectMapper.writeValueAsString(buildDTO("sneaky@test.com", "pass123", "ROLE_ADMIN"));

            mockMvc.perform(post("/api/v1/users/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_RH"))
                    .andExpect(jsonPath("$.roles.length()").value(1));
        }

        @Test
        @DisplayName("an authenticated admin can register another admin")
        void register_byAdmin_canGrantAdmin() throws Exception {
            createUser("root@test.com", "pass", RoleName.ROLE_ADMIN);
            String token = getAuthToken("root@test.com", "pass");

            mockMvc.perform(post("/api/v1/users/register")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildDTO("second-admin@test.com", "pass123", "ROLE_ADMIN"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.roles[0]").value("ROLE_ADMIN"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/users/login")
    class Login {

        @Test
        @DisplayName("login with correct credentials returns 200 and a JWT token")
        void login_valid_credentials_returns_token() throws Exception {
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("login@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());

            String loginBody = "{\"email\":\"login@test.com\",\"password\":\"pass\"}";

            mockMvc.perform(post("/api/v1/users/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty())
                    .andExpect(jsonPath("$.user.email").value("login@test.com"));
        }

        @Test
        @DisplayName("login with wrong password returns 401")
        void login_wrong_password_returns_error() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("badpwd@test.com", "correct", "ROLE_RH"))))
                    .andExpect(status().isCreated());

            String loginBody = "{\"email\":\"badpwd@test.com\",\"password\":\"wrong\"}";

            mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users")
    class GetAllUsers {

        @Test
        @DisplayName("admin can list all users")
        void getAllUsers_admin_returns_200() throws Exception {
            createUser("list@test.com", "pass", RoleName.ROLE_ADMIN);

            String token = getAuthToken("list@test.com", "pass");

            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }

        @Test
        @DisplayName("non-admin user gets 403")
        void getAllUsers_rhUser_returns_403() throws Exception {
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("list-rh@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());

            String token = getAuthToken("list-rh@test.com", "pass");

            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", token))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void getAllUsers_unauthenticated_returns_401() throws Exception {
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/{id} — disable user")
    class DisableUser {

        @Test
        @DisplayName("admin can disable another user — returns 204")
        void disableUser_returns_204() throws Exception {
            createUser("admin-dis@test.com", "pass", RoleName.ROLE_ADMIN);

            String registerResponse = mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("target-dis@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();

            Long targetId = objectMapper.readTree(registerResponse).get("id").asLong();
            String token = getAuthToken("admin-dis@test.com", "pass");

            mockMvc.perform(delete("/api/v1/users/" + targetId)
                            .header("Authorization", token))
                    .andExpect(status().isNoContent());
        }
    }


    @Nested
    @DisplayName("GET /api/v1/users/me")
    class GetMe {

        @Test
        @DisplayName("authenticated user gets their own profile — returns 200 with email")
        void getMe_authenticated_returns_200() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("me@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("me@test.com", "pass");

            mockMvc.perform(get("/api/v1/users/me")
                            .header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("me@test.com"));
        }

        @Test
        @DisplayName("unauthenticated request returns 401")
        void getMe_anonymousUser_returns_401() throws Exception {
            mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me — update profile")
    class UpdateProfile {

        @Test
        @DisplayName("authenticated user updates first/last name — returns 200")
        void updateProfile_authenticated_returns_200() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("profile@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("profile@test.com", "pass");

            String body = "{\"firstName\":\"Updated\",\"lastName\":\"Name\"}";

            mockMvc.perform(patch("/api/v1/users/me")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.firstName").value("Updated"))
                    .andExpect(jsonPath("$.lastName").value("Name"));
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/me/email — change email")
    class ChangeEmail {

        @Test
        @DisplayName("correct current password and free email — returns 200 with new email")
        void changeEmail_correctPassword_returns_200() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("oldemail@test.com", "pass123", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("oldemail@test.com", "pass123");

            String body = "{\"newEmail\":\"newemail@test.com\",\"currentPassword\":\"pass123\"}";

            mockMvc.perform(put("/api/v1/users/me/email")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("newemail@test.com"));
        }

        @Test
        @DisplayName("wrong current password returns 400")
        void changeEmail_wrongPassword_throws() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("emailwrong@test.com", "correct", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("emailwrong@test.com", "correct");

            String body = "{\"newEmail\":\"other@test.com\",\"currentPassword\":\"wrongpwd\"}";

            mockMvc.perform(put("/api/v1/users/me/email")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("new email already taken returns 400")
        void changeEmail_emailAlreadyInUse_throws() throws Exception {
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("emailuser1@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("emailuser2@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("emailuser1@test.com", "pass");

            String body = "{\"newEmail\":\"emailuser2@test.com\",\"currentPassword\":\"pass\"}";

            mockMvc.perform(put("/api/v1/users/me/email")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/me/password — change password")
    class ChangePassword {

        @Test
        @DisplayName("correct current password — returns 204")
        void changePassword_correctPassword_returns_204() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("changepwd@test.com", "oldpass", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("changepwd@test.com", "oldpass");

            String body = "{\"currentPassword\":\"oldpass\",\"newPassword\":\"newpass12\"}";

            mockMvc.perform(put("/api/v1/users/me/password")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("wrong current password returns 400")
        void changePassword_wrongPassword_throws() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("pwdwrong@test.com", "correct", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("pwdwrong@test.com", "correct");

            String body = "{\"currentPassword\":\"wrongpwd\",\"newPassword\":\"newpass12\"}";

            mockMvc.perform(put("/api/v1/users/me/password")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/users/me/preferences — update preferences")
    class UpdatePreferences {

        @Test
        @DisplayName("authenticated user updates theme and language — returns 200")
        void updatePreferences_authenticated_returns_200() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("prefs@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("prefs@test.com", "pass");

            String body = "{\"theme\":\"dark\",\"language\":\"fr\",\"emailNotificationsEnabled\":false}";

            mockMvc.perform(put("/api/v1/users/me/preferences")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me — soft-delete own account")
    class DeleteMe {

        @Test
        @DisplayName("authenticated user soft-deletes their account — returns 204")
        void deleteMe_authenticated_returns_204() throws Exception {
             
            mockMvc.perform(post("/api/v1/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildDTO("deleteme@test.com", "pass", "ROLE_RH"))))
                    .andExpect(status().isCreated());
            String token = getAuthToken("deleteme@test.com", "pass");

            mockMvc.perform(delete("/api/v1/users/me")
                            .header("Authorization", token))
                    .andExpect(status().isNoContent());
        }
    }
}
