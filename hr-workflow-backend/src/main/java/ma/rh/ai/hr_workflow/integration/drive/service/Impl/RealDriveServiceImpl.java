package ma.rh.ai.hr_workflow.integration.drive.service.Impl;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
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
//@Primary
@RequiredArgsConstructor
public class RealDriveServiceImpl implements DriveService {

    private final ObjectMapper objectMapper;

    @Value("${google.credentials.path:}")
    private String credentialsPath;

    @Override
    public String saveFile(String configJson, String inputData) throws Exception {
        try {
            log.info("Uploading file to Google Drive...");

            // Parse configuration
            DriveConfigDTO config = objectMapper.readValue(configJson, DriveConfigDTO.class);
            DriveRequestDTO request = objectMapper.readValue(inputData, DriveRequestDTO.class);

            String folderId = config.getFolderId();
            String fileName = request.getFileName() != null ? request.getFileName() : "workflow-output.txt";
            String fileContent = request.getContent() != null ? request.getContent() : inputData;

            // Initialize Drive service
            Drive driveService = getDriveService();

            // Create temporary file
            java.io.File tempFile = java.io.File.createTempFile("workflow-", ".txt");
            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(fileContent);
            }

            // Upload to Google Drive
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

            // Clean up temp file
            tempFile.delete();

            log.info("✅ File uploaded to Drive: {}", fileId);

            // Create response
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

    private Drive getDriveService() throws Exception {
        if (credentialsPath == null || credentialsPath.isEmpty()) {
            throw new RuntimeException("Google credentials path not configured. Set google.credentials.path in application.properties");
        }

        // Load credentials
        GoogleCredentials credentials;

        if (credentialsPath.startsWith("classpath:")) {
            String path = credentialsPath.replace("classpath:", "");
            credentials = GoogleCredentials.fromStream(
                    getClass().getClassLoader().getResourceAsStream(path)
            ).createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.file"));
        } else {
            credentials = GoogleCredentials.fromStream(new FileInputStream(credentialsPath))
                    .createScoped(Collections.singletonList("https://www.googleapis.com/auth/drive.file"));
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