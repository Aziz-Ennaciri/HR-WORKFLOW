package ma.rh.ai.hr_workflow.exceptions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GptServiceException")
class GptServiceExceptionTest {

    @Test
    @DisplayName("constructor stores message and cause")
    void constructor_storesMessageAndCause() {
        Throwable cause = new RuntimeException("gpt error");
        GptServiceException ex = new GptServiceException("gpt failed", cause);

        assertThat(ex.getMessage()).isEqualTo("gpt failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
