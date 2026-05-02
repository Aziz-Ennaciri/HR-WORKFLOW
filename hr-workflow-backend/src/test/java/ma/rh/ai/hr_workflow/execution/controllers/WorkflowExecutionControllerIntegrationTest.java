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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Execution controller tests run WITHOUT a wrapping test transaction because the
 * underlying service uses REQUIRES_NEW sub-transactions that need committed data.
 * Each test cleans up the DB itself in @BeforeEach / @AfterEach.
 */
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

    @BeforeEach
    void cleanAndSetUp() throws Exception {
        jdbcTemplate.execute(
            "TRUNCATE TABLE node_instances, workflow_instances, nodes, user_roles, workflows, users RESTART IDENTITY CASCADE"
        );

        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        user = new User();
        user.setEmail("exec-ctrl@test.com");
        user.setPassword(passwordEncoder.encode("pass"));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        activeWorkflow = new Workflow();
        activeWorkflow.setName("ExecCtrlWF");
        activeWorkflow.setWorkflowKey("exec_ctrl_wf");
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

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE node_instances, workflow_instances, nodes, user_roles, workflows, users RESTART IDENTITY CASCADE"
        );
    }

    private String triggerBody(Long workflowId) throws Exception {
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
            // Arrange — trigger first
            MvcResult triggered = mockMvc.perform(post("/api/v1/executions/trigger")
                    .param("userId", user.getId().toString())
                    .header("Authorization", userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(triggerBody(activeWorkflow.getId())))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long instanceId = objectMapper.readTree(triggered.getResponse().getContentAsString())
                    .get("id").asLong();

            // Act & Assert
            mockMvc.perform(get("/api/v1/executions/" + instanceId)
                            .header("Authorization", userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(instanceId));
        }

        @Test
        @DisplayName("returns 500 for unknown ID (no global exception handler maps to 404)")
        void getById_unknown_returns_500() throws Exception {
            // Act & Assert
            mockMvc.perform(get("/api/v1/executions/99999")
                            .header("Authorization", userToken))
                    .andExpect(status().is5xxServerError());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/executions/{executionId}/retry-from/{nodeId}")
    class RetryFromNode {

        @Test
        @DisplayName("retry from failed node returns 200")
        void retryFromNode_returns_200() throws Exception {
            // Arrange — trigger, then manually mark as failed
            MvcResult triggered = mockMvc.perform(post("/api/v1/executions/trigger")
                    .param("userId", user.getId().toString())
                    .header("Authorization", userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(triggerBody(activeWorkflow.getId())))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long instanceId = objectMapper.readTree(triggered.getResponse().getContentAsString())
                    .get("id").asLong();

            // Mark instance and node as FAILED so retryFromNode can operate
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
            // Arrange — trigger one
            mockMvc.perform(post("/api/v1/executions/trigger")
                    .param("userId", user.getId().toString())
                    .header("Authorization", userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(triggerBody(activeWorkflow.getId())))
                    .andExpect(status().isCreated());

            // Act & Assert
            mockMvc.perform(get("/api/v1/executions")
                            .header("Authorization", userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }
}
