package ma.rh.ai.hr_workflow.integration.gpt.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.exceptions.service.GptServiceException;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptConfigDTO;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptRequestDTO;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptResponseDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GptServiceImpl")
class GptServiceImplTest {

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    GptServiceImpl service;


    @Test
    @DisplayName("analyze with a non-null model uses the configured model name")
    void analyze_withNonNullModel_usesConfiguredModel() throws Exception {
        GptConfigDTO config = new GptConfigDTO();
        config.setModel("gpt-3.5-turbo");
        GptRequestDTO request = new GptRequestDTO("Analyze text", null);

        when(objectMapper.readValue(anyString(), eq(GptConfigDTO.class))).thenReturn(config);
        when(objectMapper.readValue(anyString(), eq(GptRequestDTO.class))).thenReturn(request);

        ArgumentCaptor<GptResponseDTO> captor = ArgumentCaptor.forClass(GptResponseDTO.class);
        when(objectMapper.writeValueAsString(captor.capture())).thenReturn("{\"model\":\"gpt-3.5-turbo\"}");

        String result = service.analyze("{}", "{}");

        assertThat(result).isEqualTo("{\"model\":\"gpt-3.5-turbo\"}");
        assertThat(captor.getValue().getModel()).isEqualTo("gpt-3.5-turbo");
    }

    @Test
    @DisplayName("analyze with null model defaults to gpt-4")
    void analyze_withNullModel_defaultsToGpt4() throws Exception {
        GptConfigDTO config = new GptConfigDTO();
        config.setModel(null);
        GptRequestDTO request = new GptRequestDTO("Analyze text", null);

        when(objectMapper.readValue(anyString(), eq(GptConfigDTO.class))).thenReturn(config);
        when(objectMapper.readValue(anyString(), eq(GptRequestDTO.class))).thenReturn(request);

        ArgumentCaptor<GptResponseDTO> captor = ArgumentCaptor.forClass(GptResponseDTO.class);
        when(objectMapper.writeValueAsString(captor.capture())).thenReturn("{}");

        service.analyze("{}", "{}");

        assertThat(captor.getValue().getModel()).isEqualTo("gpt-4");
    }

    @Test
    @DisplayName("analyze with thread interrupt sets re-interrupt flag and throws GptServiceException")
    void analyze_threadInterrupted_throwsGptServiceExceptionAndReinterrupts() throws Exception {
        GptConfigDTO config = new GptConfigDTO();
        config.setModel("gpt-4");
        GptRequestDTO request = new GptRequestDTO("text", null);

        when(objectMapper.readValue(anyString(), eq(GptConfigDTO.class))).thenReturn(config);
        when(objectMapper.readValue(anyString(), eq(GptRequestDTO.class))).thenReturn(request);

        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.analyze("{}", "{}"))
                .isInstanceOf(GptServiceException.class)
                .hasMessage("GPT request was interrupted");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("analyze propagates IOException as GptServiceException with communication message")
    void analyze_ioException_throwsGptServiceException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(GptConfigDTO.class)))
                .thenThrow(new JsonProcessingException("network error") {});

        assertThatThrownBy(() -> service.analyze("{}", "{}"))
                .isInstanceOf(GptServiceException.class)
                .hasMessage("Error communicating with GPT service");
    }

    @Test
    @DisplayName("analyze wraps unexpected RuntimeException as GptServiceException with unexpected message")
    void analyze_unexpectedException_throwsGptServiceException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(GptConfigDTO.class)))
                .thenThrow(new RuntimeException("unexpected failure"));

        assertThatThrownBy(() -> service.analyze("{}", "{}"))
                .isInstanceOf(GptServiceException.class)
                .hasMessage("Unexpected GPT service error");
    }
}
