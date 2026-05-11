package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.exceptions.service.ExcelServiceException;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelConfigDTO;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelRequestDTO;
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
@DisplayName("DummyExcelServiceImpl")
class DummyExcelServiceImplTest {

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    DummyExcelServiceImpl service;


    @Test
    @DisplayName("processData succeeds and returns serialized JSON response")
    void processData_success_returnsJson() throws Exception {
        when(objectMapper.readValue(anyString(), eq(ExcelConfigDTO.class))).thenReturn(new ExcelConfigDTO());
        when(objectMapper.readValue(anyString(), eq(ExcelRequestDTO.class))).thenReturn(new ExcelRequestDTO());
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"rowsProcessed\":150}");

        String result = service.processData("{}", "{}");

        assertThat(result).isEqualTo("{\"rowsProcessed\":150}");
    }

    @Test
    @DisplayName("processData with thread interrupt sets re-interrupt flag and throws ExcelServiceException")
    void processData_threadInterrupted_throwsExcelServiceExceptionAndReinterrupts() throws Exception {
        when(objectMapper.readValue(anyString(), eq(ExcelConfigDTO.class))).thenReturn(new ExcelConfigDTO());
        when(objectMapper.readValue(anyString(), eq(ExcelRequestDTO.class))).thenReturn(new ExcelRequestDTO());

        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.processData("{}", "{}"))
                .isInstanceOf(ExcelServiceException.class)
                .hasMessage("Dummy Excel service interrupted");

        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    @DisplayName("processData wraps unexpected Exception as ExcelServiceException with generic message")
    void processData_unexpectedException_throwsExcelServiceException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(ExcelConfigDTO.class)))
                .thenThrow(new RuntimeException("parse failed"));

        assertThatThrownBy(() -> service.processData("{}", "{}"))
                .isInstanceOf(ExcelServiceException.class)
                .hasMessage("Dummy Excel service failed");
    }
}
