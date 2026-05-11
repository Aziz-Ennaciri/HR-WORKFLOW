package ma.rh.ai.hr_workflow.integration.email.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.exceptions.service.EmailServiceException;
import ma.rh.ai.hr_workflow.integration.email.DTOs.EmailRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DummyEmailServiceImpl")
class DummyEmailServiceImplTest {

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    DummyEmailServiceImpl service;


    @Test
    @DisplayName("sendEmail succeeds and returns serialized JSON response")
    void sendEmail_success_returnsJson() throws Exception {
        EmailRequestDTO request = new EmailRequestDTO("to@example.com", "Subject", "Body", null, false);
        when(objectMapper.readValue(anyString(), eq(EmailRequestDTO.class))).thenReturn(request);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"messageId\":\"msg_abc\"}");

        String result = service.sendEmail("{}", "{}", "MyWorkflow", "wf-key");

        assertThat(result).isEqualTo("{\"messageId\":\"msg_abc\"}");
    }

    @Test
    @DisplayName("sendEmail with thread interrupt sets re-interrupt flag and throws EmailServiceException")
    void sendEmail_threadInterrupted_throwsEmailServiceExceptionAndReinterrupts() throws Exception {
        EmailRequestDTO request = new EmailRequestDTO("to@example.com", "Subject", "Body", null, false);
        when(objectMapper.readValue(anyString(), eq(EmailRequestDTO.class))).thenReturn(request);

        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.sendEmail("{}", "{}", "wf", "key"))
                .isInstanceOf(EmailServiceException.class)
                .hasMessage("Email operation was interrupted");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("sendEmail wraps unexpected Exception as EmailServiceException with generic message")
    void sendEmail_unexpectedException_throwsEmailServiceException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(EmailRequestDTO.class)))
                .thenThrow(new RuntimeException("unexpected"));

        assertThatThrownBy(() -> service.sendEmail("{}", "{}", "wf", "key"))
                .isInstanceOf(EmailServiceException.class)
                .hasMessage("Error while processing email request");
    }
}
