package ma.rh.ai.hr_workflow.integration.drive.service.Impl;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveConfigDTO;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveResponseDTO;
import ma.rh.ai.hr_workflow.integration.drive.service.DriveService;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class LocalDriveServiceImpl implements DriveService {

    private final ObjectMapper objectMapper;

    @Value("${drive.storage.path:/tmp/hr-workflow-files}")
    private String storagePath;

    @Override
    public String saveFile(String configJson, String inputData) throws Exception {
        try {
            log.info("💾 Local Drive: Saving file...");

            DriveConfigDTO config = objectMapper.readValue(configJson, DriveConfigDTO.class);

            // Use fields from your DriveConfigDTO
            String fileName = config.getFileName() != null ? config.getFileName() : generateFileName();
            String folderId = config.getFolderId() != null ? config.getFolderId() : "default";

            // Create directory structure
            Path folderPath = Paths.get(storagePath, folderId);
            Files.createDirectories(folderPath);

            // Create file path
            Path filePath = folderPath.resolve(fileName);

            // Write content to file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(inputData);
            }

            File file = filePath.toFile();
            long fileSize = file.length();

            log.info("✅ Local Drive: File saved - {} ({} bytes)", fileName, fileSize);

            // Build response
            DriveResponseDTO response = new DriveResponseDTO();
            response.setFileId(file.getName());
            response.setFileName(fileName);
            response.setFileUrl("file://" + filePath.toAbsolutePath());
            response.setWebViewLink(filePath.toAbsolutePath().toString());
            response.setSize(fileSize);
            response.setCreatedAt(LocalDateTime.now());

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("❌ Local Drive service failed", e);
            throw new RuntimeException("Local Drive upload failed: " + e.getMessage(), e);
        }
    }

    private String generateFileName() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "workflow_output_" + timestamp + ".txt";
    }
}