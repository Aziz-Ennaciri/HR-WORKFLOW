package ma.rh.ai.hr_workflow.execution.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalNodeHandler")
class ApprovalNodeHandlerTest {

    @Mock private UserRepository userRepository;

    private ObjectMapper objectMapper;
    private ApprovalNodeHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new ApprovalNodeHandler(userRepository, objectMapper);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private Node nodeWithConfig(String configJson) {
        Node n = new Node();
        n.setId(1L);
        n.setConfigJson(configJson);
        return n;
    }

    private User buildUser(Long id, String email) {
        User u = new User();
        u.setId(id);
        u.setEmail(email);
        return u;
    }

    // ─── getType ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getType()")
    class GetType {

        @Test
        @DisplayName("returns NodeType.APPROVAL")
        void getType_returnsApproval() {
            assertThat(handler.getType()).isEqualTo(NodeType.APPROVAL);
        }
    }

    // ─── execute — happy paths ────────────────────────────────────────────────────

    @Nested
    @DisplayName("execute() — happy paths")
    class ExecuteHappyPaths {

        @Test
        @DisplayName("happy path — approverEmail found → assigns user to nodeInstance, returns APPROVAL_SIGNAL")
        void execute_approverEmailFound_assignsUser() throws Exception {
            // Arrange
            Node node = nodeWithConfig("{\"approverEmail\":\"manager@company.com\"}");
            NodeInstance nodeInstance = new NodeInstance();
            User approver = buildUser(5L, "manager@company.com");
            when(userRepository.findByEmail("manager@company.com")).thenReturn(Optional.of(approver));

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            assertThat(nodeInstance.getAssignedTo()).isEqualTo(approver);
        }

        @Test
        @DisplayName("happy path — approverEmail not in user repo → no assignment, still returns APPROVAL_SIGNAL")
        void execute_approverEmailNotFound_noAssignment() throws Exception {
            // Arrange
            Node node = nodeWithConfig("{\"approverEmail\":\"unknown@company.com\"}");
            NodeInstance nodeInstance = new NodeInstance();
            when(userRepository.findByEmail("unknown@company.com")).thenReturn(Optional.empty());

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            assertThat(nodeInstance.getAssignedTo()).isNull();
        }

        @Test
        @DisplayName("happy path — configJson has no approverEmail field → no user lookup, returns APPROVAL_SIGNAL")
        void execute_noApproverEmailField_noUserLookup() throws Exception {
            // Arrange
            Node node = nodeWithConfig("{\"timeout\":\"48h\",\"requireComment\":true}");
            NodeInstance nodeInstance = new NodeInstance();

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            verify(userRepository, never()).findByEmail(any());
            assertThat(nodeInstance.getAssignedTo()).isNull();
        }

        @Test
        @DisplayName("happy path — approverEmail is empty string → no user lookup, returns APPROVAL_SIGNAL")
        void execute_blankApproverEmail_noUserLookup() throws Exception {
            // Arrange
            Node node = nodeWithConfig("{\"approverEmail\":\"\"}");
            NodeInstance nodeInstance = new NodeInstance();

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("happy path — approverEmail is whitespace → no user lookup, returns APPROVAL_SIGNAL")
        void execute_whitespaceApproverEmail_noUserLookup() throws Exception {
            // Arrange
            Node node = nodeWithConfig("{\"approverEmail\":\"   \"}");
            NodeInstance nodeInstance = new NodeInstance();

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            verify(userRepository, never()).findByEmail(any());
        }
    }

    // ─── execute — null / blank configJson ───────────────────────────────────────

    @Nested
    @DisplayName("execute() — null or blank configJson")
    class ExecuteNullBlankConfig {

        @Test
        @DisplayName("null configJson — skips parsing, returns APPROVAL_SIGNAL without throwing")
        void execute_nullConfig_returnsSignal() throws Exception {
            // Arrange
            Node node = nodeWithConfig(null);
            NodeInstance nodeInstance = new NodeInstance();

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            verify(userRepository, never()).findByEmail(any());
        }

        @Test
        @DisplayName("blank configJson — skips parsing, returns APPROVAL_SIGNAL without throwing")
        void execute_blankConfig_returnsSignal() throws Exception {
            // Arrange
            Node node = nodeWithConfig("   ");
            NodeInstance nodeInstance = new NodeInstance();

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            verify(userRepository, never()).findByEmail(any());
        }
    }

    // ─── execute — invalid configJson (fault tolerance) ──────────────────────────

    @Nested
    @DisplayName("execute() — invalid configJson (graceful degradation)")
    class ExecuteInvalidConfig {

        @Test
        @DisplayName("malformed JSON — logs warning and returns APPROVAL_SIGNAL without throwing")
        void execute_malformedJson_returnsSignalWithoutThrowing() throws Exception {
            // Arrange
            Node node = nodeWithConfig("{this is not valid json");
            NodeInstance nodeInstance = new NodeInstance();

            // Act — must NOT throw; handler catches and logs the exception
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            verify(userRepository, never()).findByEmail(any());
            assertThat(nodeInstance.getAssignedTo()).isNull();
        }

        @Test
        @DisplayName("JSON array instead of object — logs warning, returns APPROVAL_SIGNAL")
        void execute_jsonArray_returnsSignal() throws Exception {
            // Arrange
            Node node = nodeWithConfig("[\"not\",\"an\",\"object\"]");
            NodeInstance nodeInstance = new NodeInstance();

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
        }

        @Test
        @DisplayName("empty JSON object — no approverEmail, no user lookup, returns APPROVAL_SIGNAL")
        void execute_emptyJsonObject_returnsSignal() throws Exception {
            // Arrange
            Node node = nodeWithConfig("{}");
            NodeInstance nodeInstance = new NodeInstance();

            // Act
            String result = handler.execute(node, nodeInstance);

            // Assert
            assertThat(result).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
            verify(userRepository, never()).findByEmail(any());
        }
    }

    // ─── execute — APPROVAL_SIGNAL constant ──────────────────────────────────────

    @Nested
    @DisplayName("APPROVAL_SIGNAL constant")
    class ApprovalSignalConstant {

        @Test
        @DisplayName("APPROVAL_SIGNAL equals '__WAITING_APPROVAL__'")
        void approvalSignal_hasExpectedValue() {
            assertThat(ApprovalNodeHandler.APPROVAL_SIGNAL).isEqualTo("__WAITING_APPROVAL__");
        }
    }
}
