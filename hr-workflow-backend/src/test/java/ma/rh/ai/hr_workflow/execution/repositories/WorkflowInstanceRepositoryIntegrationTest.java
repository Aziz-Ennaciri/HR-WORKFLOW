package ma.rh.ai.hr_workflow.execution.repositories;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RoleRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import ma.rh.ai.hr_workflow.workflow.repositories.WorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkflowInstanceRepository Integration Tests")
class WorkflowInstanceRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WorkflowInstancerepository instanceRepository;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private User user;
    private Workflow workflow;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        user = new User();
        user.setEmail("exec-test@test.com");
        user.setPassword(passwordEncoder.encode("pass"));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        workflow = new Workflow();
        workflow.setName("ExecWorkflow");
        workflow.setWorkflowKey("exec_wf");
        workflow.setStatus(WorkflowStatus.ACTIVE);
        workflow.setVersion(1);
        workflow.setCreatedBy(user);
        workflow = workflowRepository.save(workflow);
    }

    private WorkflowInstance buildInstance(WorkflowInstanceStatus status) {
        WorkflowInstance i = new WorkflowInstance();
        i.setWorkflow(workflow);
        i.setTriggeredBy(user);
        i.setStatus(status);
        i.setCreatedAt(LocalDateTime.now());
        return i;
    }

    @Nested
    @DisplayName("findByWorkflowIdOrderByCreatedAtDesc")
    class FindByWorkflowId {

        @Test
        @DisplayName("returns all instances for the workflow ordered by createdAt desc")
        void returns_instances_for_workflow() {
            // Arrange
            instanceRepository.save(buildInstance(WorkflowInstanceStatus.COMPLETED));
            instanceRepository.save(buildInstance(WorkflowInstanceStatus.FAILED));

            // Act
            List<WorkflowInstance> result =
                    instanceRepository.findByWorkflowIdOrderByCreatedAtDesc(workflow.getId());

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(i -> i.getWorkflow().getId().equals(workflow.getId()));
        }

        @Test
        @DisplayName("returns empty list when no instances exist for the workflow")
        void returns_empty_for_unknown_workflow() {
            // Act
            List<WorkflowInstance> result = instanceRepository.findByWorkflowIdOrderByCreatedAtDesc(99999L);

            // Assert
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByWorkflowIdAndStatusIn — status filter")
    class StatusFilter {

        @Test
        @DisplayName("returns only instances with the requested statuses")
        void filter_by_status_in() {
            // Arrange
            instanceRepository.save(buildInstance(WorkflowInstanceStatus.RUNNING));
            instanceRepository.save(buildInstance(WorkflowInstanceStatus.PENDING));
            instanceRepository.save(buildInstance(WorkflowInstanceStatus.COMPLETED));

            // Act
            List<WorkflowInstance> active = instanceRepository.findByWorkflowIdAndStatusIn(
                    workflow.getId(),
                    List.of(WorkflowInstanceStatus.RUNNING, WorkflowInstanceStatus.PENDING));

            // Assert
            assertThat(active).hasSize(2);
            assertThat(active).extracting(WorkflowInstance::getStatus)
                    .containsExactlyInAnyOrder(
                            WorkflowInstanceStatus.RUNNING,
                            WorkflowInstanceStatus.PENDING);
        }
    }

    @Nested
    @DisplayName("findByWorkflow_CreatedBy_Email — user executions")
    class FindByCreatorEmail {

        @Test
        @DisplayName("returns instances belonging to workflows created by the given email")
        void returns_instances_for_creator_email() {
            // Arrange
            instanceRepository.save(buildInstance(WorkflowInstanceStatus.COMPLETED));

            // Act
            List<WorkflowInstance> result =
                    instanceRepository.findByWorkflow_CreatedBy_Email(user.getEmail());

            // Assert
            assertThat(result).isNotEmpty();
            assertThat(result).allMatch(i ->
                    i.getWorkflow().getCreatedBy().getEmail().equals(user.getEmail()));
        }
    }

    @Nested
    @DisplayName("status transition persistence")
    class StatusTransition {

        @Test
        @DisplayName("status change from PENDING to RUNNING is persisted")
        void status_transition_persisted() {
            // Arrange
            WorkflowInstance instance = instanceRepository.save(buildInstance(WorkflowInstanceStatus.PENDING));

            // Act
            instance.setStatus(WorkflowInstanceStatus.RUNNING);
            instanceRepository.save(instance);

            // Assert
            WorkflowInstance reloaded = instanceRepository.findById(instance.getId()).orElseThrow();
            assertThat(reloaded.getStatus()).isEqualTo(WorkflowInstanceStatus.RUNNING);
        }
    }
}
