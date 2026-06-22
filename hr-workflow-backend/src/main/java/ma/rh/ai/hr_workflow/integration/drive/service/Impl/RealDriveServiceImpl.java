package ma.rh.ai.hr_workflow.integration.drive.service.Impl;

import java.io.FileWriter;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Collections;

import ma.rh.ai.hr_workflow.exceptions.service.DriveServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveConfigDTO;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveRequestDTO;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveResponseDTO;
import ma.rh.ai.hr_workflow.integration.drive.service.DriveService;

@Slf4j
@Service
@RequiredArgsConstructor
public class RealDriveServiceImpl implements DriveService {

    private final ObjectMapper objectMapper;

    @Value("${google.credentials.path:}")
    private String credentialsPath;

    @Override
    public String saveFile(String configJson, String inputData) {
        try {
            log.info("Uploading file to Google Drive...");

            DriveConfigDTO config = objectMapper.readValue(configJson, DriveConfigDTO.class);
            DriveRequestDTO request = objectMapper.readValue(inputData, DriveRequestDTO.class);

            String folderId = config.getFolderId();
            String fileName = request.getFileName() != null ? request.getFileName() : "workflow-output.txt";
            String fileContent = request.getContent() != null ? request.getContent() : inputData;

            Drive driveService = getDriveService();

            java.io.File tempFile = java.io.File.createTempFile("workflow-", ".txt");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(fileContent);
            }

            File fileMetadata = new File();
            fileMetadata.setName(fileName);
            if (folderId != null && !folderId.isEmpty()) {
                fileMetadata.setParents(Collections.singletonList(folderId));
            }

            FileContent mediaContent = new FileContent("text/plain", tempFile);
            File uploadedFile = driveService.files().create(fileMetadata, mediaContent)
                    .setFields("id, webViewLink, size, createdTime")
                    .execute();

            String fileId = uploadedFile.getId();

            if (!tempFile.delete()) {
                log.warn("Failed to delete temporary file: {}", tempFile.getAbsolutePath());
            }

            log.info("✅ File uploaded to Drive: {}", fileId);

            DriveResponseDTO response = new DriveResponseDTO();
            response.setFileId(fileId);
            response.setFileName(fileName);
            response.setFileUrl("https://drive.google.com/file/d/" + fileId + "/view");
            response.setWebViewLink(uploadedFile.getWebViewLink());
            response.setSize(uploadedFile.getSize());
            response.setCreatedAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("❌ Failed to upload to Drive", e);
            throw new RuntimeException("Drive upload failed: " + e.getMessage());
        }
    }

    protected Drive getDriveService() throws Exception {

        if (credentialsPath == null || credentialsPath.isEmpty()) {

            throw new DriveServiceException(
                    "Google credentials path not configured"
            );
        }

        GoogleCredentials credentials;

        if (credentialsPath.startsWith("classpath:")) {

            String path = credentialsPath.replace("classpath:", "");

            InputStream credentialsStream =
                    getClass().getClassLoader().getResourceAsStream(path);

            if (credentialsStream == null) {

                throw new DriveServiceException(
                        "Google credentials file not found in classpath: " + path
                );
            }

            credentials = GoogleCredentials.fromStream(credentialsStream)
                    .createScoped(
                            Collections.singletonList(
                                    "https://www.googleapis.com/auth/drive.file"
                            )
                    );

        } else {

            credentials = GoogleCredentials
                    .fromStream(new java.io.FileInputStream(credentialsPath))
                    .createScoped(
                            Collections.singletonList(
                                    "https://www.googleapis.com/auth/drive.file"
                            )
                    );
        }

        return new Drive.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        )
                .setApplicationName("HR Workflow System")
                .build();
    }
}