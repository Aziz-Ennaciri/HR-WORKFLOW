package ma.rh.ai.hr_workflow.execution.handler;

import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.integration.email.service.EmailService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailNodeHandler")
class EmailNodeHandlerTest {

    @Mock
    private EmailService emailService;

    private EmailNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new EmailNodeHandler(emailService);
    }

    @Test
    @DisplayName("getType() returns NodeType.EMAIL")
    void getType_returnsEmail() {
        assertThat(handler.getType()).isEqualTo(NodeType.EMAIL);
    }

    @Test
    @DisplayName("execute() delegates to emailService with configJson, inputData, workflowName, workflowKey")
    void execute_delegatesCorrectArguments() throws Exception {
        // Arrange
        Workflow wf = new Workflow();
        wf.setName("HR Onboarding");
        wf.setWorkflowKey("hr-onboarding");

        WorkflowInstance wfInstance = new WorkflowInstance();
        wfInstance.setWorkflow(wf);

        NodeInstance ni = new NodeInstance();
        ni.setInputData("Alice|5yr|Java");
        ni.setWorkflowInstance(wfInstance);

        Node node = new Node();
        node.setConfigJson("{\"to\":\"hr@company.com\"}");

        when(emailService.sendEmail(any(), any(), any(), any())).thenReturn("sent");

        // Act
        String result = handler.execute(node, ni);

        // Assert
        assertThat(result).isEqualTo("sent");
        verify(emailService).sendEmail(
                "{\"to\":\"hr@company.com\"}",
                "Alice|5yr|Java",
                "HR Onboarding",
                "hr-onboarding");
    }
}
