package ma.rh.ai.hr_workflow.workflow.repositories;

import ma.rh.ai.hr_workflow.config.AbstractIntegrationTest;
import ma.rh.ai.hr_workflow.user.model.Role;
import ma.rh.ai.hr_workflow.user.model.RoleName;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.RoleRepository;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NodeRepository Integration Tests")
class NodeRepositoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired private NodeRepository nodeRepository;
    @Autowired private WorkflowRepository workflowRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private Workflow workflow;

    @BeforeEach
    void setUp() {
        Role role = roleRepository.findByName(RoleName.ROLE_RH).orElseThrow();
        User user = new User();
        user.setEmail("node-test@test.com");
        user.setPassword(passwordEncoder.encode("pass"));
        user.setEnabled(true);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        workflow = new Workflow();
        workflow.setName("NodeTestWorkflow");
        workflow.setStatus(WorkflowStatus.DRAFT);
        workflow.setVersion(1);
        workflow.setCreatedBy(user);
        workflow = workflowRepository.save(workflow);
    }

    private Node buildNode(NodeType type, int order) {
        Node n = new Node();
        n.setWorkflow(workflow);
        n.setType(type);
        n.setOrderIndex(order);
        return n;
    }

    @Nested
    @DisplayName("findByWorkflowIdOrderByOrderIndexAsc — ordering")
    class Ordering {

        @Test
        @DisplayName("returns nodes sorted by orderIndex ascending")
        void nodes_are_returned_in_order() {
            // Arrange
            nodeRepository.save(buildNode(NodeType.EMAIL, 3));
            nodeRepository.save(buildNode(NodeType.GPT, 1));
            nodeRepository.save(buildNode(NodeType.DRIVE, 2));

            // Act
            List<Node> ordered = nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflow.getId());

            // Assert
            assertThat(ordered).hasSize(3);
            assertThat(ordered.get(0).getOrderIndex()).isEqualTo(1);
            assertThat(ordered.get(1).getOrderIndex()).isEqualTo(2);
            assertThat(ordered.get(2).getOrderIndex()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("findByWorkflowIdAndType — type filter")
    class TypeFilter {

        @Test
        @DisplayName("returns only nodes of the requested type")
        void filter_by_node_type() {
            // Arrange
            nodeRepository.save(buildNode(NodeType.EMAIL, 1));
            nodeRepository.save(buildNode(NodeType.EMAIL, 2));
            nodeRepository.save(buildNode(NodeType.GPT, 3));

            // Act
            List<Node> emailNodes = nodeRepository.findByWorkflowIdAndType(workflow.getId(), NodeType.EMAIL);
            List<Node> gptNodes = nodeRepository.findByWorkflowIdAndType(workflow.getId(), NodeType.GPT);
            List<Node> driveNodes = nodeRepository.findByWorkflowIdAndType(workflow.getId(), NodeType.DRIVE);

            // Assert
            assertThat(emailNodes).hasSize(2).allMatch(n -> n.getType() == NodeType.EMAIL);
            assertThat(gptNodes).hasSize(1).allMatch(n -> n.getType() == NodeType.GPT);
            assertThat(driveNodes).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteByWorkflowId — cascade")
    class CascadeDelete {

        @Test
        @DisplayName("deleteByWorkflowId removes all nodes of that workflow")
        void deleteByWorkflowId_removes_all_nodes() {
            // Arrange
            nodeRepository.save(buildNode(NodeType.EMAIL, 1));
            nodeRepository.save(buildNode(NodeType.GPT, 2));
            assertThat(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflow.getId())).hasSize(2);

            // Act
            nodeRepository.deleteByWorkflowId(workflow.getId());

            // Assert
            assertThat(nodeRepository.findByWorkflowIdOrderByOrderIndexAsc(workflow.getId())).isEmpty();
        }
    }
}
