package ma.rh.ai.hr_workflow.execution.repositories;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NodeInstanceRepository Integration Tests")
class NodeInstanceRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private NodeInstanceRepository nodeInstanceRepository;
    @Autowired private WorkflowInstancerepository workflowInstanceRepository;
    @Autowired private NodeRepository nodeRepository;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private WorkflowInstance workflowInstance;
    private Node node1;
    private Node node2;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();

        User user = new User();
        user.setEmail("ni-test@test.com");
        user.setPassword(passwordEncoder.encode("pass"));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        Workflow workflow = new Workflow();
        workflow.setName("NITestWorkflow");
        workflow.setStatus(WorkflowStatus.ACTIVE);
        workflow.setVersion(1);
        workflow.setCreatedBy(user);
        workflow = workflowRepository.save(workflow);

        node1 = new Node();
        node1.setWorkflow(workflow);
        node1.setType(NodeType.EMAIL);
        node1.setOrderIndex(1);
        node1 = nodeRepository.save(node1);

        node2 = new Node();
        node2.setWorkflow(workflow);
        node2.setType(NodeType.GPT);
        node2.setOrderIndex(2);
        node2 = nodeRepository.save(node2);

        workflowInstance = new WorkflowInstance();
        workflowInstance.setWorkflow(workflow);
        workflowInstance.setTriggeredBy(user);
        workflowInstance.setStatus(WorkflowInstanceStatus.RUNNING);
        workflowInstance.setCreatedAt(LocalDateTime.now());
        workflowInstance = workflowInstanceRepository.save(workflowInstance);
    }

    private NodeInstance buildNodeInstance(Node node, NodeInstanceStatus status, int order) {
        NodeInstance ni = new NodeInstance();
        ni.setWorkflowInstance(workflowInstance);
        ni.setNode(node);
        ni.setExecutionOrder(order);
        ni.setStatus(status);
        ni.setCreatedAt(LocalDateTime.now());
        return ni;
    }

    @Nested
    @DisplayName("findByWorkflowInstanceIdAndNodeId")
    class FindByInstanceAndNode {

        @Test
        @DisplayName("finds the specific node instance for a workflow instance and node")
        void finds_correct_node_instance() {
            // Arrange
            nodeInstanceRepository.save(buildNodeInstance(node1, NodeInstanceStatus.COMPLETED, 1));
            nodeInstanceRepository.save(buildNodeInstance(node2, NodeInstanceStatus.PENDING, 2));

            // Act
            Optional<NodeInstance> found = nodeInstanceRepository
                    .findByWorkflowInstanceIdAndNodeId(workflowInstance.getId(), node1.getId());

            // Assert
            assertThat(found).isPresent();
            assertThat(found.get().getNode().getId()).isEqualTo(node1.getId());
            assertThat(found.get().getStatus()).isEqualTo(NodeInstanceStatus.COMPLETED);
        }

        @Test
        @DisplayName("returns empty when the combination does not exist")
        void returns_empty_for_unknown_combination() {
            // Act & Assert
            Optional<NodeInstance> found = nodeInstanceRepository
                    .findByWorkflowInstanceIdAndNodeId(99999L, 99999L);
            assertThat(found).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByWorkflowInstanceIdOrderByExecutionOrderAsc")
    class FindOrdered {

        @Test
        @DisplayName("returns node instances sorted by executionOrder ascending")
        void returns_ordered_node_instances() {
            // Arrange
            nodeInstanceRepository.save(buildNodeInstance(node2, NodeInstanceStatus.PENDING, 2));
            nodeInstanceRepository.save(buildNodeInstance(node1, NodeInstanceStatus.COMPLETED, 1));

            // Act
            List<NodeInstance> ordered = nodeInstanceRepository
                    .findByWorkflowInstanceIdOrderByExecutionOrderAsc(workflowInstance.getId());

            // Assert
            assertThat(ordered).hasSize(2);
            assertThat(ordered.get(0).getExecutionOrder()).isEqualTo(1);
            assertThat(ordered.get(1).getExecutionOrder()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("error message persistence")
    class ErrorPersistence {

        @Test
        @DisplayName("error message saved on a FAILED node instance is retrievable")
        void errorMessage_persisted_and_retrievable() {
            // Arrange
            NodeInstance ni = buildNodeInstance(node1, NodeInstanceStatus.FAILED, 1);
            ni.setErrorMessage("GPT timeout after 30s");
            nodeInstanceRepository.save(ni);

            // Act
            NodeInstance reloaded = nodeInstanceRepository.findById(ni.getId()).orElseThrow();

            // Assert
            assertThat(reloaded.getStatus()).isEqualTo(NodeInstanceStatus.FAILED);
            assertThat(reloaded.getErrorMessage()).isEqualTo("GPT timeout after 30s");
        }
    }
}
