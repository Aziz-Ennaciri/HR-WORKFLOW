package ma.rh.ai.hr_workflow.workflow.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RoleRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("WorkflowController Integration Tests")
class WorkflowControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private User adminUser;
    private User rhUser;
    private String adminToken;
    private String rhToken;

    @BeforeEach
    void setUp() throws Exception {
        Role adminRole = roleRepository.findByName(RoleName.ROLE_ADMIN).orElseThrow();
        Role rhRole = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        adminUser = new User();
        adminUser.setEmail("wf-admin@test.com");
        adminUser.setPassword(passwordEncoder.encode("pass"));
        adminUser.setEnabled(true);
        adminUser.setRoles(Set.of(adminRole));
        adminUser = userRepository.save(adminUser);

        rhUser = new User();
        rhUser.setEmail("wf-rh@test.com");
        rhUser.setPassword(passwordEncoder.encode("pass"));
        rhUser.setEnabled(true);
        rhUser.setRoles(Set.of(rhRole));
        rhUser = userRepository.save(rhUser);

        adminToken = getAuthToken("wf-admin@test.com", "pass");
        rhToken = getAuthToken("wf-rh@test.com", "pass");
    }

    private String createWorkflowBody(String name, String key) throws Exception {
        CreateWorkflowDTO dto = new CreateWorkflowDTO();
        dto.setName(name);
        dto.setWorkflowKey(key);
        dto.setDescription("desc");
        return objectMapper.writeValueAsString(dto);
    }

    @Nested
    @DisplayName("POST /api/v1/workflows — create")
    class CreateWorkflow {

        @Test
        @DisplayName("create workflow returns 201 with workflow body")
        void create_workflow_returns_201() throws Exception {
            // Arrange
            String body = createWorkflowBody("Recruitment WF", "recruit_wf");

            // Act & Assert
            mockMvc.perform(post("/api/v1/workflows")
                            .param("creatorId", adminUser.getId().toString())
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.name").value("Recruitment WF"))
                    .andExpect(jsonPath("$.status").value("DRAFT"));
        }

        @Test
        @DisplayName("create workflow without auth still returns 201 (security is permitAll)")
        void create_workflow_no_auth_returns_201() throws Exception {
            // Act & Assert — security config uses anyRequest().permitAll(), so no token still succeeds
            mockMvc.perform(post("/api/v1/workflows")
                            .param("creatorId", adminUser.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createWorkflowBody("NoAuth", "no_auth")))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workflows — list")
    class GetWorkflows {

        @Test
        @DisplayName("admin sees all workflows regardless of creator")
        void admin_sees_all_workflows() throws Exception {
            // Arrange — RH creates its own workflow
            mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", rhUser.getId().toString())
                    .header("Authorization", rhToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("RH-WF", "rh_wf")))
                    .andExpect(status().isCreated());

            // Act — admin GETs all
            MvcResult result = mockMvc.perform(get("/api/v1/workflows")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert
            JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(list.isArray()).isTrue();
            assertThat(list.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("RH user sees only their own workflows")
        void rh_user_sees_only_own_workflows() throws Exception {
            // Arrange
            mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", rhUser.getId().toString())
                    .header("Authorization", rhToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("MY-WF", "my_wf")))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("ADMIN-WF", "admin_wf")))
                    .andExpect(status().isCreated());

            // Act
            MvcResult result = mockMvc.perform(get("/api/v1/workflows")
                            .header("Authorization", rhToken))
                    .andExpect(status().isOk())
                    .andReturn();

            // Assert — RH only sees their own
            JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(list.isArray()).isTrue();
            for (JsonNode wf : list) {
                assertThat(wf.get("createdByEmail").asText()).isEqualTo("wf-rh@test.com");
            }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workflows/{id} — by ID")
    class GetById {

        @Test
        @DisplayName("returns the workflow when it exists")
        void getById_existing_returns_200() throws Exception {
            // Arrange
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("FindMe", "find_me")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

            // Act & Assert
            mockMvc.perform(get("/api/v1/workflows/" + id)
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("FindMe"));
        }

        @Test
        @DisplayName("throws for a non-existent workflow (unhandled RuntimeException)")
        void getById_nonExistent_throws() {
            assertThatThrownBy(() ->
                mockMvc.perform(get("/api/v1/workflows/99999")
                        .header("Authorization", adminToken)))
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/workflows/{id} — update")
    class UpdateWorkflow {

        @Test
        @DisplayName("update workflow returns 200 with updated name")
        void update_workflow_returns_200() throws Exception {
            // Arrange — create
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("OldName", "old_key")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

            UpdateWorkflowDTO update = new UpdateWorkflowDTO();
            update.setName("NewName");
            update.setWorkflowKey("new_key");

            // Act & Assert
            mockMvc.perform(put("/api/v1/workflows/" + id)
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(update)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("NewName"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/workflows/{id} — soft delete")
    class DeleteWorkflow {

        @Test
        @DisplayName("delete workflow returns 204 No Content")
        void delete_workflow_returns_204() throws Exception {
            // Arrange
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("ToDelete", "to_del")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

            // Act & Assert
            mockMvc.perform(delete("/api/v1/workflows/" + id)
                            .header("Authorization", adminToken))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/workflows/{id}/duplicate")
    class DuplicateWorkflow {

        @Test
        @DisplayName("duplicate workflow returns 201 with new workflow ID")
        void duplicate_workflow_returns_201() throws Exception {
            // Arrange
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("OriginalDup", "orig_dup")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long originalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

            // Act
            MvcResult dupResult = mockMvc.perform(post("/api/v1/workflows/" + originalId + "/duplicate")
                            .header("Authorization", adminToken))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert — new ID is different from the original
            Long newId = objectMapper.readTree(dupResult.getResponse().getContentAsString()).get("id").asLong();
            assertThat(newId).isNotEqualTo(originalId);
        }
    }
}
