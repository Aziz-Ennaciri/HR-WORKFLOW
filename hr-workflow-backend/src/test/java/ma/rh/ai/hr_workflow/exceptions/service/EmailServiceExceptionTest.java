package ma.rh.ai.hr_workflow.exceptions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EmailServiceException")
class EmailServiceExceptionTest {

    @Test
    @DisplayName("constructor stores message and cause")
    void constructor_storesMessageAndCause() {
        Throwable cause = new RuntimeException("smtp error");
        EmailServiceException ex = new EmailServiceException("email failed", cause);

        assertThat(ex.getMessage()).isEqualTo("email failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
