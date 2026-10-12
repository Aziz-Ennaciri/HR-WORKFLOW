package ma.rh.ai.hr_workflow.execution.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ApprovalNodeHandler")
class ApprovalNodeHandlerTest {

    private final ApprovalNodeHandler handler = new ApprovalNodeHandler(new ObjectMapper());

    @Test
    @DisplayName("getType() returns NodeType.APPROVAL")
    void getType_returnsApproval() {
        assertThat(handler.getType()).isEqualTo(NodeType.APPROVAL);
    }

    @Test
    @DisplayName("execute() always asks the engine to pause")
    void execute_returnsApprovalSignal() {
        NodeExecutionContext context = new NodeExecutionContext(1L, 2L, NodeType.APPROVAL,
                "{\"approverEmail\":\"boss@test.com\"}", "input", "input", "Hiring");

        assertThat(handler.execute(context)).isEqualTo(ApprovalNodeHandler.APPROVAL_SIGNAL);
        assertThat(ApprovalNodeHandler.APPROVAL_SIGNAL).isEqualTo("__WAITING_APPROVAL__");
    }

    @Test
    @DisplayName("approverEmail() reads the configured approver")
    void approverEmail_present() {
        assertThat(handler.approverEmail("{\"approverEmail\":\"boss@test.com\",\"instructions\":\"check\"}"))
                .contains("boss@test.com");
    }

    @ParameterizedTest(name = "config {0} has no approver")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "{}", "{\"approverEmail\":\"\"}", "{\"approverEmail\":\"  \"}", "[1,2]", "{not json"})
    @DisplayName("approverEmail() is empty for missing, blank or malformed config")
    void approverEmail_absent(String configJson) {
        assertThat(handler.approverEmail(configJson)).isEmpty();
    }
}
