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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


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
    void cleanAndSetUp() {
        truncateWithRetry();

        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        user = new User();
        user.setEmail("exec-svc@test.com");
        user.setPassword(passwordEncoder.encode("pass"));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        activeWorkflow = new Workflow();
        activeWorkflow.setName("ActiveWorkflow");
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

    @Nested
    @DisplayName("triggerWorkflow")
    class TriggerWorkflow {

        @Test
        @DisplayName("creates a WorkflowInstance with PENDING status for an ACTIVE workflow")
        void trigger_active_workflow_creates_pending_instance() {
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(activeWorkflow.getId());
            dto.setInputData("{\"candidate\":\"Alice\"}");

            WorkflowInstance instance = executionService.triggerWorkflow(dto, user);

            assertThat(instance).isNotNull();
            assertThat(instance.getId()).isNotNull();
            assertThat(instance.getStatus()).isEqualTo(WorkflowInstanceStatus.PENDING);
            assertThat(instance.getWorkflow().getId()).isEqualTo(activeWorkflow.getId());
            assertThat(instance.getTriggeredBy().getId()).isEqualTo(user.getId());
        }

        @Test
        @DisplayName("triggering persists node instances for each workflow node")
        void trigger_creates_node_instances_for_each_node() {
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(activeWorkflow.getId());

            WorkflowInstance instance = executionService.triggerWorkflow(dto, user);

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
            Workflow draft = new Workflow();
            draft.setName("Draft");
            draft.setStatus(WorkflowStatus.DRAFT);
            draft.setVersion(1);
            draft.setCreatedBy(user);
            draft = workflowRepository.save(draft);
            final Long draftId = draft.getId();

            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(draftId);

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
            TriggerWorkflowInstanceDTO dto = new TriggerWorkflowInstanceDTO();
            dto.setWorkflowId(activeWorkflow.getId());
            WorkflowInstance instance = executionService.triggerWorkflow(dto, user);

            instance.setStatus(WorkflowInstanceStatus.FAILED);
            instance.setErrorMessage("Simulated failure");
            instanceRepository.save(instance);

            NodeInstance nodeInstance = nodeInstanceRepository
                    .findByWorkflowInstanceIdOrderByExecutionOrderAsc(instance.getId())
                    .get(0);
            nodeInstance.setStatus(NodeInstanceStatus.FAILED);
            nodeInstance.setErrorMessage("Email send timeout");
            nodeInstanceRepository.save(nodeInstance);

            executionService.retryFromNode(instance.getId(), nodeInstance.getNode().getId());
            WorkflowInstance updated = instanceRepository.findById(instance.getId()).orElseThrow();
            assertThat(updated).isNotNull();
            assertThat(updated.getId()).isEqualTo(instance.getId());
        }
    }
}
