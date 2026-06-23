package ma.rh.ai.hr_workflow.execution.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RoleRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.NodeRepository;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("WorkflowExecutionController Integration Tests")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowExecutionControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private NodeRepository nodeRepository;
    @Autowired private WorkflowInstancerepository instanceRepository;
    @Autowired private NodeInstanceRepository nodeInstanceRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private User user;
    private Workflow activeWorkflow;
    private Node emailNode;
    private String userToken;

    private void truncateWithRetry() {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                jdbcTemplate.execute(
                    "TRUNCATE TABLE node_instances, workflow_instances, nodes, user_roles, workflows, users RESTART IDENTITY CASCADE"
                );
                return;
            } catch (DataAccessException e) {
                if (attempt == 2) throw e;
            }
        }
    }

    @BeforeEach
    void cleanAndSetUp() throws Exception {
        truncateWithRetry();

        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        user = new User();
        user.setEmail("exec-ctrl@test.com");
        user.setPassword(passwordEncoder.encode("pass"));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        activeWorkflow = new Workflow();
        activeWorkflow.setName("ExecCtrlWF");
        activeWorkflow.setStatus(WorkflowStatus.ACTIVE);
        activeWorkflow.setVersion(1);
        activeWorkflow.setCreatedBy(user);
        activeWorkflow = workflowRepository.save(activeWorkflow);

        emailNode = new Node();
        emailNode.setWorkflow(activeWorkflow);
        emailNode.setType(NodeType.EMAIL);
        emailNode.setOrderIndex(1);
        emailNode = nodeRepository.save(emailNode);

        userToken = getAuthToken("exec-ctrl@test.com", "pass");
    }

    private String triggerBody(Long workflowId){
        return "{\"workflowId\":" + workflowId + "}";
    }

    @Nested
    @DisplayName("POST /api/v1/executions/trigger")
    class Trigger {

        @Test
        @DisplayName("triggering an ACTIVE workflow returns 201 with instance ID")
        void trigger_active_workflow_returns_201() throws Exception {
            // Act
            MvcResult result = mockMvc.perform(post("/api/v1/executions/trigger")
                            .param("userId", user.getId().toString())
                            .header("Authorization", userToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(triggerBody(activeWorkflow.getId())))
                    .andExpect(status().isCreated())
                    .andReturn();

            // Assert
            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(body.get("id").asLong()).isPositive();
            assertThat(body.get("status").asText()).isEqualTo("PENDING");
        }
    }

    @Nested
    @DisplayName("GET /api/v1/executions/{id}")
    class GetInstance {

        @Test
        @DisplayName("returns existing workflow instance")
        void getById_existing_returns_200() throws Exception {
            MvcResult triggered = mockMvc.perform(post("/api/v1/executions/trigger")
                    .param("userId", user.getId().toString())
                    .header("Authorization", userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(triggerBody(activeWorkflow.getId())))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long instanceId = objectMapper.readTree(triggered.getResponse().getContentAsString())
                    .get("id").asLong();

            mockMvc.perform(get("/api/v1/executions/" + instanceId)
                            .header("Authorization", userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(instanceId));
        }

        @Test
        @DisplayName("throws for unknown ID (unhandled RuntimeException in Spring 6 MockMvc)")
        void getById_unknown_throws(){
            assertThatThrownBy(() ->
                mockMvc.perform(get("/api/v1/executions/99999")
                        .header("Authorization", userToken)))
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("POST /api/v1/executions/{executionId}/retry-from/{nodeId}")
    class RetryFromNode {

        @Test
        @DisplayName("retry from failed node returns 200")
        void retryFromNode_returns_200() throws Exception {
            MvcResult triggered = mockMvc.perform(post("/api/v1/executions/trigger")
                    .param("userId", user.getId().toString())
                    .header("Authorization", userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(triggerBody(activeWorkflow.getId())))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long instanceId = objectMapper.readTree(triggered.getResponse().getContentAsString())
                    .get("id").asLong();

            WorkflowInstance instance = instanceRepository.findById(instanceId).orElseThrow();
            instance.setStatus(WorkflowInstanceStatus.FAILED);
            instanceRepository.save(instance);

            NodeInstance ni = nodeInstanceRepository
                    .findByWorkflowInstanceIdOrderByExecutionOrderAsc(instanceId)
                    .get(0);
            ni.setStatus(NodeInstanceStatus.FAILED);
            ni.setErrorMessage("Email timeout");
            nodeInstanceRepository.save(ni);

            // Act & Assert
            mockMvc.perform(post("/api/v1/executions/" + instanceId + "/retry-from/" + ni.getNode().getId())
                            .header("Authorization", userToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/executions — user isolation")
    class GetAllExecutions {

        @Test
        @DisplayName("authenticated user sees their own workflow executions")
        void getAllExecutions_returns_own_instances() throws Exception {
            mockMvc.perform(post("/api/v1/executions/trigger")
                    .param("userId", user.getId().toString())
                    .header("Authorization", userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(triggerBody(activeWorkflow.getId())))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/api/v1/executions")
                            .header("Authorization", userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
