package ma.rh.ai.hr_workflow.integration.drive.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import ma.rh.ai.hr_workflow.exceptions.service.DriveServiceException;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveConfigDTO;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RealDriveServiceImpl")
class RealDriveServiceImplTest {

    @Mock
    ObjectMapper objectMapper;

    @InjectMocks
    RealDriveServiceImpl service;


    @Test
    @DisplayName("getDriveService throws when credentialsPath is null")
    void getDriveService_nullPath_throwsDriveServiceException() {
        // credentialsPath is null (default after @InjectMocks without Spring)
        assertThatThrownBy(service::getDriveService)
                .isInstanceOf(DriveServiceException.class)
                .hasMessage("Google credentials path not configured");
    }

    @Test
    @DisplayName("getDriveService throws when credentialsPath is empty")
    void getDriveService_emptyPath_throwsDriveServiceException() {
        ReflectionTestUtils.setField(service, "credentialsPath", "");

        assertThatThrownBy(service::getDriveService)
                .isInstanceOf(DriveServiceException.class)
                .hasMessage("Google credentials path not configured");
    }

    @Test
    @DisplayName("getDriveService throws when classpath resource is not found")
    void getDriveService_classpathResourceMissing_throwsDriveServiceException() {
        ReflectionTestUtils.setField(service, "credentialsPath", "classpath:nonexistent-credentials.json");

        assertThatThrownBy(service::getDriveService)
                .isInstanceOf(DriveServiceException.class)
                .hasMessageContaining("Google credentials file not found in classpath");
    }

    @Test
    @DisplayName("getDriveService throws when file-system path does not exist")
    void getDriveService_fileSystemPathMissing_throwsException() {
        ReflectionTestUtils.setField(service, "credentialsPath", "/nonexistent/credentials.json");

        assertThatThrownBy(service::getDriveService)
                .isInstanceOf(Exception.class);
    }


    @Test
    @DisplayName("saveFile with null fileName falls back to default name and throws when Drive unavailable")
    void saveFile_nullFileName_usesDefault() throws Exception {
        DriveConfigDTO config = new DriveConfigDTO();
        DriveRequestDTO request = new DriveRequestDTO(null, null, null);

        when(objectMapper.readValue(anyString(), eq(DriveConfigDTO.class))).thenReturn(config);
        when(objectMapper.readValue(anyString(), eq(DriveRequestDTO.class))).thenReturn(request);

        assertThatThrownBy(() -> service.saveFile("{}", "{}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drive upload failed");
    }

    @Test
    @DisplayName("saveFile with non-null fileName uses provided name and throws when Drive unavailable")
    void saveFile_nonNullFileName_usesProvidedName() throws Exception {
        DriveConfigDTO config = new DriveConfigDTO();
        DriveRequestDTO request = new DriveRequestDTO("some content", "report.txt", "text/plain");

        when(objectMapper.readValue(anyString(), eq(DriveConfigDTO.class))).thenReturn(config);
        when(objectMapper.readValue(anyString(), eq(DriveRequestDTO.class))).thenReturn(request);

        assertThatThrownBy(() -> service.saveFile("{}", "{}"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Drive upload failed");
    }

    @Test
    @DisplayName("saveFile succeeds without folderId via mocked Drive")
    void saveFile_noFolderId_success() throws Exception {
        DriveConfigDTO config = new DriveConfigDTO();
        DriveRequestDTO request = new DriveRequestDTO("content", "file.txt", "text/plain");

        RealDriveServiceImpl spy = Mockito.spy(service);
        Drive mockDrive = mock(Drive.class);
        Drive.Files mockFiles = mock(Drive.Files.class);
        Drive.Files.Create mockCreate = mock(Drive.Files.Create.class);
        com.google.api.services.drive.model.File uploadedFile =
                mock(com.google.api.services.drive.model.File.class);

        doReturn(mockDrive).when(spy).getDriveService();
        when(objectMapper.readValue(anyString(), eq(DriveConfigDTO.class))).thenReturn(config);
        when(objectMapper.readValue(anyString(), eq(DriveRequestDTO.class))).thenReturn(request);
        when(mockDrive.files()).thenReturn(mockFiles);
        when(mockFiles.create(
                any(com.google.api.services.drive.model.File.class),
                any(FileContent.class))
        ).thenReturn(mockCreate);
        when(mockCreate.setFields(anyString())).thenReturn(mockCreate);
        when(mockCreate.execute()).thenReturn(uploadedFile);
        when(uploadedFile.getId()).thenReturn("fileId123");
        when(uploadedFile.getWebViewLink()).thenReturn("https://drive.google.com/file");
        when(uploadedFile.getSize()).thenReturn(1024L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"fileId\":\"fileId123\"}");

        String result = spy.saveFile("{}", "{}");

        assertThat(result).isEqualTo("{\"fileId\":\"fileId123\"}");
    }

    @Test
    @DisplayName("saveFile succeeds with non-empty folderId sets parents via mocked Drive")
    void saveFile_withFolderId_setsParents() throws Exception {
        DriveConfigDTO config = new DriveConfigDTO();
        config.setFolderId("folder-abc");
        DriveRequestDTO request = new DriveRequestDTO("content", "file.txt", "text/plain");

        RealDriveServiceImpl spy = Mockito.spy(service);
        Drive mockDrive = mock(Drive.class);
        Drive.Files mockFiles = mock(Drive.Files.class);
        Drive.Files.Create mockCreate = mock(Drive.Files.Create.class);
        com.google.api.services.drive.model.File uploadedFile =
                mock(com.google.api.services.drive.model.File.class);

        doReturn(mockDrive).when(spy).getDriveService();
        when(objectMapper.readValue(anyString(), eq(DriveConfigDTO.class))).thenReturn(config);
        when(objectMapper.readValue(anyString(), eq(DriveRequestDTO.class))).thenReturn(request);
        when(mockDrive.files()).thenReturn(mockFiles);
        when(mockFiles.create(
                any(com.google.api.services.drive.model.File.class),
                any(FileContent.class))
        ).thenReturn(mockCreate);
        when(mockCreate.setFields(anyString())).thenReturn(mockCreate);
        when(mockCreate.execute()).thenReturn(uploadedFile);
        when(uploadedFile.getId()).thenReturn("file999");
        when(uploadedFile.getWebViewLink()).thenReturn("https://drive.google.com/link");
        when(uploadedFile.getSize()).thenReturn(2048L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"fileId\":\"file999\"}");

        String result = spy.saveFile("{}", "{}");

        assertThat(result).isEqualTo("{\"fileId\":\"file999\"}");
    }
}
