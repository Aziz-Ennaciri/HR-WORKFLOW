package ma.rh.ai.hr_workflow.execution.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.NodeInstanceRepository;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstanceRepository;
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
    @Autowired private WorkflowInstanceRepository instanceRepository;
    @Autowired private NodeInstanceRepository nodeInstanceRepository;
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
        @DisplayName("returns 404 for unknown ID")
        void getById_unknown_returns_404() throws Exception {
            mockMvc.perform(get("/api/v1/executions/99999")
                        .header("Authorization", userToken))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/executions/{executionId}/retry-from/{nodeId}")
    class RetryFromNode {

        @Test
        @DisplayName("retry from failed node returns 200")
        void retryFromNode_returns_200() throws Exception {
            MvcResult triggered = mockMvc.perform(post("/api/v1/executions/trigger")
                    .header("Authorization", userToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(triggerBody(activeWorkflow.getId())))
                    .andExpect(status().isCreated())
                    .andReturn();
            Long instanceId = objectMapper.readTree(triggered.getResponse().getContentAsString())
                    .get("id").asLong();

            // There is no SMTP server in tests, so the real run fails at the EMAIL step.
            await("execution to fail", () -> instanceRepository.findById(instanceId).orElseThrow()
                    .getStatus() == WorkflowInstanceStatus.FAILED);
            NodeInstance ni = nodeInstanceRepository
                    .findByWorkflowInstanceIdOrderByExecutionOrderAsc(instanceId)
                    .get(0);

            // Act & Assert
            mockMvc.perform(post("/api/v1/executions/" + instanceId + "/retry-from/" + ni.getNode().getId())
                            .header("Authorization", userToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/executions and /stats — paging, filters, isolation")
    class ListAndStats {

        /** Stored directly (no engine run) so statuses and order are deterministic. */
        private void storeExecution(Workflow workflow, User triggeredBy, WorkflowInstanceStatus status, int minutesAgo) {
            WorkflowInstance execution = new WorkflowInstance();
            execution.setWorkflow(workflow);
            execution.setTriggeredBy(triggeredBy);
            execution.setStatus(status);
            WorkflowInstance saved = instanceRepository.save(execution);
            jdbcTemplate.update("UPDATE workflow_instances SET created_at = now() - (? * interval '1 minute') WHERE id = ?",
                    minutesAgo, saved.getId());
        }

        @BeforeEach
        void storeExecutions() {
            storeExecution(activeWorkflow, user, WorkflowInstanceStatus.COMPLETED, 5);
            storeExecution(activeWorkflow, user, WorkflowInstanceStatus.COMPLETED, 4);
            storeExecution(activeWorkflow, user, WorkflowInstanceStatus.FAILED, 3);
            storeExecution(activeWorkflow, user, WorkflowInstanceStatus.COMPLETED, 2);
            storeExecution(activeWorkflow, user, WorkflowInstanceStatus.FAILED, 1);

            User other = createUser("other-exec@test.com", "pass", RoleName.ROLE_RH);
            Workflow othersWorkflow = new Workflow();
            othersWorkflow.setName("OtherWF");
            othersWorkflow.setStatus(WorkflowStatus.ACTIVE);
            othersWorkflow.setVersion(1);
            othersWorkflow.setCreatedBy(other);
            storeExecution(workflowRepository.save(othersWorkflow), other, WorkflowInstanceStatus.COMPLETED, 1);
        }

        @Test
        @DisplayName("returns a page of the user's own executions, newest first")
        void list_paged_newestFirst() throws Exception {
            mockMvc.perform(get("/api/v1/executions?page=0&size=2").header("Authorization", userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                    .andExpect(jsonPath("$.content[0].workflowName").value("ExecCtrlWF"))
                    .andExpect(jsonPath("$.content[1].status").value("COMPLETED"))
                    .andExpect(jsonPath("$.totalElements").value(5))
                    .andExpect(jsonPath("$.totalPages").value(3))
                    .andExpect(jsonPath("$.page").value(0));
        }

        @Test
        @DisplayName("filters by status and workflow on the server")
        void list_filtered() throws Exception {
            mockMvc.perform(get("/api/v1/executions?status=FAILED").header("Authorization", userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
            mockMvc.perform(get("/api/v1/executions?workflowId=" + activeWorkflow.getId()).header("Authorization", userToken))
                    .andExpect(jsonPath("$.totalElements").value(5));
        }

        @Test
        @DisplayName("users only see executions of their own workflows")
        void list_isolatedPerOwner() throws Exception {
            String otherToken = getAuthToken("other-exec@test.com", "pass");
            mockMvc.perform(get("/api/v1/executions").header("Authorization", otherToken))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].workflowName").value("OtherWF"));
        }

        @Test
        @DisplayName("stats count executions by status and by workflow for the current user")
        void stats() throws Exception {
            mockMvc.perform(get("/api/v1/executions/stats").header("Authorization", userToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(5))
                    .andExpect(jsonPath("$.byStatus.COMPLETED").value(3))
                    .andExpect(jsonPath("$.byStatus.FAILED").value(2))
                    .andExpect(jsonPath("$.byStatus.RUNNING").value(0))
                    .andExpect(jsonPath("$.byWorkflow.length()").value(1))
                    .andExpect(jsonPath("$.byWorkflow[0].workflowName").value("ExecCtrlWF"))
                    .andExpect(jsonPath("$.byWorkflow[0].total").value(5));
        }

        @Test
        @DisplayName("page size is capped")
        void list_pageSizeCapped() throws Exception {
            mockMvc.perform(get("/api/v1/executions?size=10000").header("Authorization", userToken))
                    .andExpect(jsonPath("$.size").value(100));
        }
    }
}
