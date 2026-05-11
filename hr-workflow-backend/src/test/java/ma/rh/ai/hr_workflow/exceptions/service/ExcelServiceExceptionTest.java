package ma.rh.ai.hr_workflow.exceptions.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExcelServiceException")
class ExcelServiceExceptionTest {

    @Test
    @DisplayName("constructor stores message and cause")
    void constructor_storesMessageAndCause() {
        Throwable cause = new RuntimeException("excel error");
        ExcelServiceException ex = new ExcelServiceException("excel failed", cause);

        assertThat(ex.getMessage()).isEqualTo("excel failed");
        assertThat(ex.getCause()).isSameAs(cause);
    }
}
