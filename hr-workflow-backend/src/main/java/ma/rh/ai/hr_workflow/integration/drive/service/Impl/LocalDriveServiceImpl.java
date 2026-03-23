package ma.rh.ai.hr_workflow.integration.drive.service.Impl;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
            DriveConfigDTO config = objectMapper.readValue(configJson, DriveConfigDTO.class);

            String action = config.getAction() != null ? config.getAction().toLowerCase() : "write";

            if ("read".equals(action)) {
                return readFilesFromFolder(config, inputData);
            } else {
                return writeFileToFolder(config, inputData);
            }

        } catch (Exception e) {
            log.error("❌ Local Drive service failed", e);
            throw new RuntimeException("Local Drive operation failed: " + e.getMessage(), e);
        }
    }

    public String readFiles(String configJson, String inputData) throws Exception {
        try {
            log.info("📖 Local Drive: Reading files from folder...");

            DriveConfigDTO config = objectMapper.readValue(configJson, DriveConfigDTO.class);
            String folderId = config.getFolderId() != null ? config.getFolderId() : "default";

            Path folderPath = Paths.get(storagePath, folderId);

            if (!Files.exists(folderPath)) {
                throw new RuntimeException("Folder not found: " + folderPath);
            }

            // Read all files in folder
            List<String> filesContent = new ArrayList<>();
            Files.list(folderPath)
                    .filter(Files::isRegularFile)
                    .forEach(filePath -> {
                        try {
                            String content = extractTextFromFile(filePath);
                            filesContent.add("=== " + filePath.getFileName() + " ===\n" + content);
                        } catch (IOException e) {
                            log.error("Failed to read file: " + filePath, e);
                        }
                    });

            String allContent = String.join("\n\n", filesContent);

            log.info("✅ Local Drive: Read {} files", filesContent.size());

            DriveResponseDTO response = new DriveResponseDTO();
            response.setFileId(folderId);
            response.setFileName(filesContent.size() + " CVs");
            response.setFileUrl("folder://" + folderPath.toAbsolutePath());
            response.setWebViewLink(folderPath.toAbsolutePath().toString());
            response.setSize((long) allContent.length());
            response.setCreatedAt(LocalDateTime.now());

            // Return the combined content for next node
            return allContent;

        } catch (Exception e) {
            log.error("❌ Local Drive read failed", e);
            throw new RuntimeException("Local Drive read failed: " + e.getMessage(), e);
        }
    }

    private String readFilesFromFolder(DriveConfigDTO config, String inputData) throws Exception {
        log.info("📖 Local Drive: Reading files from folder...");

        String folderId = config.getFolderId() != null ? config.getFolderId() : "default";
        Path folderPath = Paths.get(storagePath, folderId);

        if (!Files.exists(folderPath)) {
            throw new RuntimeException("Folder not found: " + folderPath);
        }

        // Read all files
        List<Map<String, String>> cvs = new ArrayList<>();
        Files.list(folderPath)
                .filter(Files::isRegularFile)
                .forEach(filePath -> {
                    try {
                        String content = extractTextFromFile(filePath);
                        Map<String, String> cv = new HashMap<>();
                        cv.put("fileName", filePath.getFileName().toString());
                        cv.put("content", content);
                        cvs.add(cv);
                        log.info("✅ Read file: {} ({} characters)", filePath.getFileName(), content.length());
                    } catch (IOException e) {
                        log.error("Failed to read file: " + filePath, e);
                    }
                });

        log.info("✅ Local Drive: Read {} files", cvs.size());

        // Return JSON with all CVs
        Map<String, Object> result = new HashMap<>();
        result.put("totalCVs", cvs.size());
        result.put("folder", folderId);
        result.put("cvs", cvs);

        return objectMapper.writeValueAsString(result);
    }

    /**
     * Extract text from file - supports both PDF and text files
     */
    private String extractTextFromFile(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".pdf")) {
            return extractTextFromPDF(filePath);
        } else {
            // Text file (.txt, .md, etc.)
            return Files.readString(filePath);
        }
    }

    /**
     * Extract text from PDF using Apache PDFBox
     */
    private String extractTextFromPDF(Path pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.debug("Extracted {} characters from PDF: {}", text.length(), pdfPath.getFileName());
            return text;
        } catch (Exception e) {
            log.error("Failed to extract text from PDF: " + pdfPath, e);
            throw new IOException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }

    private String writeFileToFolder(DriveConfigDTO config, String inputData) throws Exception {
        log.info("💾 Local Drive: Saving file...");

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
    }

    private String generateFileName() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return "workflow_output_" + timestamp + ".txt";
    }
}