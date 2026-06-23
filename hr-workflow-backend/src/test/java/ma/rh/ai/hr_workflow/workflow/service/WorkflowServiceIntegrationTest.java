package ma.rh.ai.hr_workflow.workflow.service;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
import ma.rh.ai.hr_workflow.execution.repositories.WorkflowInstancerepository;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RoleRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WorkflowService Integration Tests")
class WorkflowServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired private IWorkflowService workflowService;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private NodeRepository nodeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private WorkflowInstancerepository instanceRepository;

    private User userA;
    private User userB;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        userA = new User();
        userA.setEmail("wf-svc-a@test.com");
        userA.setPassword(passwordEncoder.encode("pass"));
        userA.setEnabled(true);
        userA.setRoles(Set.of(role));
        userA = userRepository.save(userA);

        userB = new User();
        userB.setEmail("wf-svc-b@test.com");
        userB.setPassword(passwordEncoder.encode("pass"));
        userB.setEnabled(true);
        userB.setRoles(Set.of(role));
        userB = userRepository.save(userB);
    }

    private CreateWorkflowDTO buildCreateDTO(String name) {
        CreateWorkflowDTO dto = new CreateWorkflowDTO();
        dto.setName(name);
        dto.setDescription("desc");
        return dto;
    }

    private Workflow buildActiveWorkflow(String name, User creator) {
        Workflow wf = new Workflow();
        wf.setName(name);
        wf.setStatus(WorkflowStatus.ACTIVE);
        wf.setVersion(1);
        wf.setCreatedBy(creator);
        return workflowRepository.save(wf);
    }

    @Nested
    @DisplayName("createWorkflow")
    class CreateWorkflow {

        @Test
        @DisplayName("creates a workflow in DRAFT status with correct creator")
        void createWorkflow_persists_with_draft_status() {
             
            CreateWorkflowDTO dto = buildCreateDTO("New WF");

             
            WorkflowResponseDTO result = workflowService.createWorkflow(dto, userA.getId());

             
            assertThat(result.getId()).isNotNull();
            assertThat(result.getName()).isEqualTo("New WF");
            assertThat(result.getStatus()).isEqualTo("DRAFT");
            assertThat(result.getCreatedByEmail()).isEqualTo(userA.getEmail());
        }
    }

    @Nested
    @DisplayName("activateWorkflow")
    class ActivateWorkflow {

        @Test
        @DisplayName("activates a DRAFT workflow that has at least one node")
        void activateWorkflow_transitions_to_ACTIVE() {
             
            WorkflowResponseDTO created = workflowService.createWorkflow(
                    buildCreateDTO("ToActivate"), userA.getId());

            Workflow wf = workflowRepository.findById(created.getId()).orElseThrow();
            Node node = new Node();
            node.setWorkflow(wf);
            node.setType(NodeType.EMAIL);
            node.setOrderIndex(1);
            node = nodeRepository.save(node);
            wf.getNodes().add(node);

             
            WorkflowResponseDTO activated = workflowService.activateWorkflow(created.getId());

             
            assertThat(activated.getStatus()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("activating a workflow without nodes throws IllegalStateException")
        void activateWorkflow_without_nodes_throws() {
             
            WorkflowResponseDTO created = workflowService.createWorkflow(
                    buildCreateDTO("EmptyWF"), userA.getId());

            assertThatThrownBy(() -> workflowService.activateWorkflow(created.getId()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("node");
        }
    }

    @Nested
    @DisplayName("deleteWorkflow — soft delete")
    class DeleteWorkflow {

        @Test
        @DisplayName("soft delete marks the workflow as deleted and cancels running instances")
        void deleteWorkflow_marksDeleted_and_cancelsInstances() {
             
            Workflow wf = buildActiveWorkflow("ToDelete", userA);

            WorkflowInstance running = new WorkflowInstance();
            running.setWorkflow(wf);
            running.setTriggeredBy(userA);
            running.setStatus(WorkflowInstanceStatus.RUNNING);
            running.setCreatedAt(LocalDateTime.now());
            instanceRepository.save(running);

             
            workflowService.deleteWorkflow(wf.getId());

            Workflow afterDelete = workflowRepository.findById(wf.getId()).orElseThrow();
            assertThat(afterDelete.isDeleted()).isTrue();

            WorkflowInstance cancelledInstance = instanceRepository.findById(running.getId()).orElseThrow();
            assertThat(cancelledInstance.getStatus()).isEqualTo(WorkflowInstanceStatus.CANCELLED);
        }

        @Test
        @DisplayName("deleted workflow does not appear in findByDeletedFalse")
        void deleted_workflow_invisible_to_deleted_false_query() {
             
            buildActiveWorkflow("StillVisible", userA);
            Workflow wf = buildActiveWorkflow("Hidden", userA);

             
            workflowService.deleteWorkflow(wf.getId());

             
            List<WorkflowResponseDTO> visible = workflowService.getAllWorkflows();
            assertThat(visible).isNotEmpty();
            assertThat(visible).extracting(WorkflowResponseDTO::getName).doesNotContain("Hidden");
        }
    }

    @Nested
    @DisplayName("duplicateWorkflow")
    class DuplicateWorkflow {

        @Test
        @DisplayName("duplicate produces a DRAFT copy with the same nodes")
        void duplicateWorkflow_creates_deep_copy() {
             
            Workflow original = buildActiveWorkflow("Original", userA);
            Node n1 = new Node();
            n1.setWorkflow(original);
            n1.setType(NodeType.EMAIL);
            n1.setOrderIndex(1);
            nodeRepository.save(n1);

            Node n2 = new Node();
            n2.setWorkflow(original);
            n2.setType(NodeType.GPT);
            n2.setOrderIndex(2);
            nodeRepository.save(n2);

             
            WorkflowResponseDTO copy = workflowService.duplicateWorkflow(original.getId(), userA.getEmail());

             
            assertThat(copy.getId()).isNotEqualTo(original.getId());
            assertThat(copy.getName()).startsWith("Copy of");
            assertThat(copy.getStatus()).isEqualTo("DRAFT");
            List<Node> copyNodes = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(copy.getId());
            assertThat(copyNodes).hasSize(2);
            assertThat(copyNodes).extracting(Node::getType)
                    .containsExactlyInAnyOrder(NodeType.EMAIL, NodeType.GPT);
        }
    }

    @Nested
    @DisplayName("user isolation")
    class UserIsolation {

        @Test
        @DisplayName("getWorkflowsByCreatorEmail returns only the calling user's workflows")
        void user_cannot_see_other_users_workflows() {
             
            workflowService.createWorkflow(buildCreateDTO("WF-A"), userA.getId());
            workflowService.createWorkflow(buildCreateDTO("WF-B"), userB.getId());

             
            List<WorkflowResponseDTO> userAResults = workflowService.getWorkflowsByCreatorEmail(userA.getEmail());
            List<WorkflowResponseDTO> userBResults = workflowService.getWorkflowsByCreatorEmail(userB.getEmail());

             
            assertThat(userAResults).extracting(WorkflowResponseDTO::getName).contains("WF-A").doesNotContain("WF-B");
            assertThat(userBResults).extracting(WorkflowResponseDTO::getName).contains("WF-B").doesNotContain("WF-A");
        }
    }
}
