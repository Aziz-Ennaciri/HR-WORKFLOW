package ma.rh.ai.hr_workflow.integration.drive.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.exceptions.service.DriveServiceException;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DummyDriveServiceImpl")
class DummyDriveServiceImplTest {

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    DummyDriveServiceImpl service;


    @Test
    @DisplayName("saveFile succeeds and returns serialized JSON response")
    void saveFile_success_returnsJson() throws Exception {
        DriveRequestDTO request = new DriveRequestDTO("file content", "report.txt", "text/plain");
        when(objectMapper.readValue(anyString(), eq(DriveRequestDTO.class))).thenReturn(request);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"fileId\":\"abc123\"}");

        String result = service.saveFile("{}", "{\"fileName\":\"report.txt\"}");

        assertThat(result).isEqualTo("{\"fileId\":\"abc123\"}");
    }

    @Test
    @DisplayName("saveFile propagates IOException as DriveServiceException with specific message")
    void saveFile_ioException_throwsDriveServiceException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(DriveRequestDTO.class)))
                .thenThrow(new JsonProcessingException("parse error") {});

        assertThatThrownBy(() -> service.saveFile("{}", "bad-json"))
                .isInstanceOf(DriveServiceException.class)
                .hasMessage("Error while uploading file to Drive");
    }

    @Test
    @DisplayName("saveFile wraps InterruptedException as DriveServiceException via catch(Exception)")
    void saveFile_threadInterrupted_throwsDriveServiceException() throws Exception {
        DriveRequestDTO request = new DriveRequestDTO("content", "file.txt", "text/plain");
        when(objectMapper.readValue(anyString(), eq(DriveRequestDTO.class))).thenReturn(request);

        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> service.saveFile("{}", "{}"))
                .isInstanceOf(DriveServiceException.class)
                .hasMessage("Unexpected Drive service error");
    }
}
