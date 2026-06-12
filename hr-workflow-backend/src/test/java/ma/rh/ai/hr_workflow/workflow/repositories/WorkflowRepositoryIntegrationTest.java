package ma.rh.ai.hr_workflow.workflow.repositories;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RoleRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkflowRepository Integration Tests")
class WorkflowRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        userA = new User();
        userA.setEmail("userA@test.com");
        userA.setPassword(passwordEncoder.encode("pass"));
        userA.setEnabled(true);
        userA.setRoles(Set.of(role));
        userA = userRepository.save(userA);

        userB = new User();
        userB.setEmail("userB@test.com");
        userB.setPassword(passwordEncoder.encode("pass"));
        userB.setEnabled(true);
        userB.setRoles(Set.of(role));
        userB = userRepository.save(userB);
    }

    private Workflow buildWorkflow(String name, User creator) {
        Workflow w = new Workflow();
        w.setName(name);
        w.setStatus(WorkflowStatus.DRAFT);
        w.setVersion(1);
        w.setCreatedBy(creator);
        return w;
    }

    @Nested
    @DisplayName("save and findById")
    class SaveAndFind {

        @Test
        @DisplayName("save persists a workflow and findById retrieves it")
        void save_and_findById_roundTrip() {
            // Arrange
            Workflow workflow = buildWorkflow("Onboarding", userA);

            // Act
            Workflow saved = workflowRepository.save(workflow);
            Optional<Workflow> found = workflowRepository.findById(saved.getId());

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getName()).isEqualTo("Onboarding");
            assertThat(found.get().getStatus()).isEqualTo(WorkflowStatus.DRAFT);
        }
    }

    @Nested
    @DisplayName("findByCreatedByEmailAndDeletedFalse — user isolation")
    class UserIsolation {

        @Test
        @DisplayName("returns only workflows belonging to the given email")
        void returns_only_owner_workflows() {
            // Arrange
            workflowRepository.save(buildWorkflow("WF-A1", userA));
            workflowRepository.save(buildWorkflow("WF-A2", userA));
            workflowRepository.save(buildWorkflow("WF-B1", userB));

            // Act
            List<Workflow> userAWorkflows = workflowRepository
                    .findByCreatedByEmailAndDeletedFalse(userA.getEmail());
            List<Workflow> userBWorkflows = workflowRepository
                    .findByCreatedByEmailAndDeletedFalse(userB.getEmail());

            // Assert
            assertThat(userAWorkflows).hasSize(2)
                    .allMatch(w -> w.getCreatedBy().getEmail().equals("userA@test.com"));
            assertThat(userBWorkflows).hasSize(1)
                    .allMatch(w -> w.getCreatedBy().getEmail().equals("userB@test.com"));
        }
    }

    @Nested
    @DisplayName("findByDeletedFalse — soft delete filter")
    class SoftDeleteFilter {

        @Test
        @DisplayName("findByDeletedFalse excludes deleted workflows")
        void findByDeletedFalse_excludes_deleted() {
            // Arrange
            workflowRepository.save(buildWorkflow("Active", userA));
            Workflow deleted = buildWorkflow("Deleted", userA);
            deleted.setDeleted(true);
            workflowRepository.save(deleted);

            // Act
            List<Workflow> result = workflowRepository.findByDeletedFalse();

            // Assert
            assertThat(result).extracting(Workflow::getName).contains("Active").doesNotContain("Deleted");
        }

        @Test
        @DisplayName("findAll returns both deleted and non-deleted workflows")
        void findAll_returns_all_including_deleted() {
            // Arrange
            workflowRepository.save(buildWorkflow("Normal", userA));
            Workflow deleted = buildWorkflow("Ghost", userA);
            deleted.setDeleted(true);
            workflowRepository.save(deleted);

            // Act
            List<Workflow> all = workflowRepository.findAll();

            // Assert
            assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        }

        @Test
        @DisplayName("soft delete hides workflow from findByDeletedFalse")
        void softDelete_hidesWorkflow_fromDeletedFalseQuery() {
            // Arrange
            workflowRepository.save(buildWorkflow("Visible", userA));
            Workflow workflow = workflowRepository.save(buildWorkflow("ToDelete", userA));

            // Act
            workflow.setDeleted(true);
            workflowRepository.save(workflow);
            List<Workflow> visible = workflowRepository.findByDeletedFalse();

            // Assert
            assertThat(visible).isNotEmpty();
            assertThat(visible).extracting(Workflow::getName).doesNotContain("ToDelete");
        }
    }
}
