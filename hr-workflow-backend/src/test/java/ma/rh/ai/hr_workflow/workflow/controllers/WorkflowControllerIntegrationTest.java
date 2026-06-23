package ma.rh.ai.hr_workflow.workflow.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RoleRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.PatchWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashSet;
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
        adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
        adminUser = userRepository.save(adminUser);

        rhUser = new User();
        rhUser.setEmail("wf-rh@test.com");
        rhUser.setPassword(passwordEncoder.encode("pass"));
        rhUser.setEnabled(true);
        rhUser.setRoles(new HashSet<>(Set.of(rhRole)));
        rhUser = userRepository.save(rhUser);

        adminToken = getAuthToken("wf-admin@test.com", "pass");
        rhToken = getAuthToken("wf-rh@test.com", "pass");
    }

    private String createWorkflowBody(String name) throws Exception {
        CreateWorkflowDTO dto = new CreateWorkflowDTO();
        dto.setName(name);
        dto.setDescription("desc");
        return objectMapper.writeValueAsString(dto);
    }

    @Nested
    @DisplayName("POST /api/v1/workflows — create")
    class CreateWorkflow {

        @Test
        @DisplayName("create workflow returns 201 with workflow body")
        void create_workflow_returns_201() throws Exception {
             
            String body = createWorkflowBody("Recruitment WF");

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
            mockMvc.perform(post("/api/v1/workflows")
                            .param("creatorId", adminUser.getId().toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(createWorkflowBody("NoAuth")))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/workflows — list")
    class GetWorkflows {

        @Test
        @DisplayName("admin sees all workflows regardless of creator")
        void admin_sees_all_workflows() throws Exception {
            mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", rhUser.getId().toString())
                    .header("Authorization", rhToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("RH-WF")))
                    .andExpect(status().isCreated());

            MvcResult result = mockMvc.perform(get("/api/v1/workflows")
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode list = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(list.isArray()).isTrue();
            assertThat(list.size()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("RH user sees only their own workflows")
        void rh_user_sees_only_own_workflows() throws Exception {
             
            mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", rhUser.getId().toString())
                    .header("Authorization", rhToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("MY-WF")))
                    .andExpect(status().isCreated());

            mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("ADMIN-WF")))
                    .andExpect(status().isCreated());

             
            MvcResult result = mockMvc.perform(get("/api/v1/workflows")
                            .header("Authorization", rhToken))
                    .andExpect(status().isOk())
                    .andReturn();

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
             
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("FindMe")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

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
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("OldName")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

            UpdateWorkflowDTO update = new UpdateWorkflowDTO();
            update.setName("NewName");

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
             
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("ToDelete")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

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
             
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("OriginalDup")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long originalId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

             
            MvcResult dupResult = mockMvc.perform(post("/api/v1/workflows/" + originalId + "/duplicate")
                            .header("Authorization", adminToken))
                    .andExpect(status().isCreated())
                    .andReturn();

            Long newId = objectMapper.readTree(dupResult.getResponse().getContentAsString()).get("id").asLong();
            assertThat(newId).isNotEqualTo(originalId);
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/workflows/{id} — patch name/description (DRAFT only)")
    class PatchWorkflow {

        @Test
        @DisplayName("DRAFT workflow name patched — returns 200 with updated name")
        void patch_draft_workflow_returns_200() throws Exception {
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("BeforePatch")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

            PatchWorkflowDTO patchDTO = new PatchWorkflowDTO("AfterPatch", "patched description");

            mockMvc.perform(patch("/api/v1/workflows/" + id)
                            .header("Authorization", adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(patchDTO)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("AfterPatch"));
        }

        @Test
        @DisplayName("patching a non-DRAFT workflow throws IllegalStateException")
        void patch_non_draft_workflow_throws() throws Exception {
            MvcResult created = mockMvc.perform(post("/api/v1/workflows")
                    .param("creatorId", adminUser.getId().toString())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createWorkflowBody("ToActivate")))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

            UpdateWorkflowDTO activateDTO = new UpdateWorkflowDTO();
            activateDTO.setName("ToActivate");
            activateDTO.setStatus(ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus.ACTIVE);
            mockMvc.perform(put("/api/v1/workflows/" + id)
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(activateDTO)))
                    .andExpect(status().isOk());

            PatchWorkflowDTO patchDTO = new PatchWorkflowDTO("ShouldFail", null);

            assertThatThrownBy(() ->
                mockMvc.perform(patch("/api/v1/workflows/" + id)
                        .header("Authorization", adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(patchDTO))))
                    .hasCauseInstanceOf(IllegalStateException.class);
        }
    }
}
