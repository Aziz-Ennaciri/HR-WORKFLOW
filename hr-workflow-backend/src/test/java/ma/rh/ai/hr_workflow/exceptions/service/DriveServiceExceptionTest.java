package ma.rh.ai.hr_workflow.exceptions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DriveServiceException")
class DriveServiceExceptionTest {

    @Test
    @DisplayName("message-only constructor stores message and has no cause")
    void messageConstructor_storesMessageNoCause() {
        DriveServiceException ex = new DriveServiceException("drive failed");

        assertThat(ex.getMessage()).isEqualTo("drive failed");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    @DisplayName("message-and-cause constructor stores both message and cause")
    void messageCauseConstructor_storesMessageAndCause() {
        Throwable cause = new RuntimeException("root cause");
        DriveServiceException ex = new DriveServiceException("drive failed", cause);

        assertThat(ex.getMessage()).isEqualTo("drive failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
