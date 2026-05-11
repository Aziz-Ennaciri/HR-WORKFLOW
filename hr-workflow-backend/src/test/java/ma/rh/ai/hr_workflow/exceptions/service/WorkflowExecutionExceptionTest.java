package ma.rh.ai.hr_workflow.exceptions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WorkflowExecutionException")
class WorkflowExecutionExceptionTest {

    @Test
    @DisplayName("message-only constructor stores message and has no cause")
    void messageConstructor_storesMessageNoCause() {
        WorkflowExecutionException ex = new WorkflowExecutionException("execution failed");

        assertThat(ex.getMessage()).isEqualTo("execution failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("message-and-cause constructor stores both message and cause")
    void messageCauseConstructor_storesMessageAndCause() {
        Throwable cause = new RuntimeException("underlying error");
        WorkflowExecutionException ex = new WorkflowExecutionException("execution failed", cause);

        assertThat(ex.getMessage()).isEqualTo("execution failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
