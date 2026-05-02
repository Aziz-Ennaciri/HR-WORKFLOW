package ma.rh.ai.hr_workflow.execution.service;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.execution.DTOs.TriggerWorkflowInstanceDTO;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Execution service tests run WITHOUT a wrapping test transaction because the service
 * internally uses REQUIRES_NEW sub-transactions (ExecutionTransactionHelper) that need
 * committed data to be visible.  Each test cleans up the DB itself in @BeforeEach.
 */
@DisplayName("WorkflowExecutionService Integration Tests")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WorkflowExecutionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private IWorkflowExecutionService executionService;
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

    @BeforeEach
    void cleanAndSetUp() {
        // Ensure clean state regardless of previous test outcome
        jdbcTemplate.execute(
            "TRUNCATE TABLE node_instances, workflow_instances, nodes, user_roles, workflows, users RESTART IDENTITY CASCADE"
        );

        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        user = new User();
        user.setEmail("exec-svc@test.com");
        user.setPassword(passwordEncoder.encode("pass"));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        activeWorkflow = new Workflow();
        activeWorkflow.setName("ActiveWorkflow");
        activeWorkflow.setWorkflowKey("active_wf_key");
        activeWorkflow.setStatus(WorkflowStatus.ACTIVE);
        activeWorkflow.setVersion(1);
        activeWorkflow.setCreatedBy(user);
        activeWorkflow = workflowRepository.save(activeWorkflow);

        emailNode = new Node();
        emailNode.setWorkflow(activeWorkflow);
        emailNode.setType(NodeType.EMAIL);
        emailNode.setOrderIndex(1);
        emailNode = nodeRepository.save(emailNode);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute(
            "TRUNCATE TABLE node_instances, workflow_instances, nodes, user_roles, workflows, users RESTART IDENTITY CASCADE"
        );
    }

    @Nested
    @DisplayName("triggerWorkflow")
    class TriggerWorkflow {

        @Test
        @DisplayName("creates a WorkflowInstance with PENDING status for an ACTIVE workflow")
        void trigger_active_workflow_creates_pending_instance() {
            // Arrange
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(activeWorkflow.getId());
            dto.setInputData("{\"candidate\":\"Alice\"}");

            // Act
            WorkflowInstance instance = executionService.triggerWorkflow(dto, user);

            // Assert
            assertThat(instance).isNotNull();
            assertThat(instance.getId()).isNotNull();
            assertThat(instance.getStatus()).isEqualTo(WorkflowInstanceStatus.PENDING);
            assertThat(instance.getWorkflow().getId()).isEqualTo(activeWorkflow.getId());
            assertThat(instance.getTriggeredBy().getId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("triggering persists node instances for each workflow node")
        void trigger_creates_node_instances_for_each_node() {
            // Arrange
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(activeWorkflow.getId());

            // Act
            WorkflowInstance instance = executionService.triggerWorkflow(dto, user);

            // Assert
            assertThat(nodeInstanceRepository
                    .findByWorkflowInstanceIdOrderByExecutionOrderAsc(instance.getId()))
                    .hasSize(1)
                    .allMatch(ni -> ni.getStatus() == NodeInstanceStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("triggerWorkflow — non-ACTIVE workflow")
    class TriggerNonActive {

        @Test
        @DisplayName("triggering a DRAFT workflow throws IllegalStateException")
        void trigger_draft_workflow_throws() {
            // Arrange
            Workflow draft = new Workflow();
            draft.setName("Draft");
            draft.setWorkflowKey("draft_key");
            draft.setStatus(WorkflowStatus.DRAFT);
            draft.setVersion(1);
            draft.setCreatedBy(user);
            draft = workflowRepository.save(draft);
            final Long draftId = draft.getId();

            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(draftId);

            // Act & Assert
            assertThatThrownBy(() -> executionService.triggerWorkflow(dto, user))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not active");
        }
    }

    @Nested
    @DisplayName("retryFromNode")
    class RetryFromNode {

        @Test
        @DisplayName("resets a FAILED node instance to PENDING and the workflow instance to PENDING")
        void retry_resets_failed_node_to_pending() {
            // Arrange — create committed state that REQUIRES_NEW can read
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(activeWorkflow.getId());
            WorkflowInstance instance = executionService.triggerWorkflow(dto, user);

            // Manually mark the instance and its node as FAILED (simulating a failed run)
            instance.setStatus(WorkflowInstanceStatus.FAILED);
            instance.setErrorMessage("Simulated failure");
            instanceRepository.save(instance);

            NodeInstance nodeInstance = nodeInstanceRepository
                    .findByWorkflowInstanceIdOrderByExecutionOrderAsc(instance.getId())
                    .get(0);
            nodeInstance.setStatus(NodeInstanceStatus.FAILED);
            nodeInstance.setErrorMessage("Email send timeout");
            nodeInstanceRepository.save(nodeInstance);

            // Act
            executionService.retryFromNode(instance.getId(), nodeInstance.getNode().getId());

            // Assert — both records reset (REQUIRES_NEW committed the changes)
            WorkflowInstance updatedInstance = instanceRepository.findById(instance.getId()).orElseThrow();
            assertThat(updatedInstance.getStatus()).isEqualTo(WorkflowInstanceStatus.PENDING);
            assertThat(updatedInstance.getErrorMessage()).isNull();

            NodeInstance updatedNode = nodeInstanceRepository
                    .findByWorkflowInstanceIdAndNodeId(instance.getId(), nodeInstance.getNode().getId())
                    .orElseThrow();
            assertThat(updatedNode.getStatus()).isEqualTo(NodeInstanceStatus.PENDING);
            assertThat(updatedNode.getErrorMessage()).isNull();
        }
    }
}
