package ma.rh.ai.hr_workflow.security;

import com.fasterxml.jackson.databind.JsonNode;
import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Authentication flow")
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = createUser("auth@test.com", "password1", RoleName.ROLE_RH);
    }

    private JsonNode login(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/users/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"));
    }

    @Test
    @DisplayName("login returns a short-lived access token and a refresh token")
    void login_returnsTokenPair() throws Exception {
        JsonNode tokens = login("auth@test.com", "password1");

        org.assertj.core.api.Assertions.assertThat(tokens.get("refreshToken").asText()).isNotBlank();
        org.assertj.core.api.Assertions.assertThat(tokens.get("expiresIn").asLong()).isEqualTo(900);
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + tokens.get("token").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("auth@test.com"));
    }

    @Test
    @DisplayName("refresh rotates the token; replaying the old one revokes every session")
    void refresh_rotates_andDetectsReplay() throws Exception {
        String first = login("auth@test.com", "password1").get("refreshToken").asText();

        String body = refresh(first).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String second = objectMapper.readTree(body).get("refreshToken").asText();

        refresh(first).andExpect(status().isUnauthorized());
        // The replay revoked the whole family, including the token issued by the legitimate refresh
        refresh(second).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("logout revokes the refresh token")
    void logout_revokes() throws Exception {
        String refreshToken = login("auth@test.com", "password1").get("refreshToken").asText();

        mockMvc.perform(post("/api/v1/users/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        refresh(refreshToken).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("disabling an account rejects its access token immediately")
    void disabledUser_rejectedImmediately() throws Exception {
        String token = "Bearer " + login("auth@test.com", "password1").get("token").asText();
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token)).andExpect(status().isOk());

        user.setEnabled(false);
        userRepository.save(user);

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("changing the email keeps the session valid (the token carries the user id)")
    void changeEmail_keepsSession() throws Exception {
        String token = "Bearer " + login("auth@test.com", "password1").get("token").asText();

        mockMvc.perform(put("/api/v1/users/me/email")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newEmail\":\"renamed@test.com\",\"currentPassword\":\"password1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("renamed@test.com"));
    }

    @Test
    @DisplayName("five failed logins lock the email out with 429, even for the right password")
    void repeatedFailures_lockOut() throws Exception {
        createUser("locked@test.com", "password1", RoleName.ROLE_RH);
        String wrong = "{\"email\":\"locked@test.com\",\"password\":\"wrong-password\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/users/login").contentType(MediaType.APPLICATION_JSON).content(wrong))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/users/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"locked@test.com\",\"password\":\"password1\"}"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    @DisplayName("registration enforces the 8-character password policy")
    void register_shortPassword_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"short@test.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }
}
