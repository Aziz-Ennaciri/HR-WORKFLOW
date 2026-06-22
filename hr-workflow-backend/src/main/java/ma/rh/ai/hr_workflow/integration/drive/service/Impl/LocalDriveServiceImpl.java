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

import com.fasterxml.jackson.databind.JsonNode;
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

    /**
     * Read all files from a folder and combine with the original inputData.
     *
     * Output structure (generic — works for any document type):
     * {
     *   "originalInput": { ...whatever the upstream node / user sent... },
     *   "fileData": { "totalFiles": N, "folder": "...", "files": [...] }
     * }
     *
     * The GPT node's prompt is responsible for interpreting the files correctly
     * (CVs, contracts, invoices, etc.). This node just reads and forwards them.
     */
    private String readFilesFromFolder(DriveConfigDTO config, String inputData) throws Exception {
        log.info("📖 Local Drive: Reading files from folder...");

        String folderId = config.getFolderId() != null ? config.getFolderId() : "default";
        Path folderPath = Paths.get(storagePath, folderId);

        if (!Files.exists(folderPath)) {
            log.warn("⚠️ Folder not found: {} — creating it and returning empty result", folderPath);
            Files.createDirectories(folderPath);
            return buildCombinedOutput(inputData, folderId, new ArrayList<>());
        }

        List<Map<String, String>> files = new ArrayList<>();
        Files.list(folderPath)
                .filter(Files::isRegularFile)
                .sorted()
                .forEach(filePath -> {
                    try {
                        String content = extractTextFromFile(filePath);
                        Map<String, String> file = new HashMap<>();
                        file.put("fileName", filePath.getFileName().toString());
                        file.put("content", content);
                        files.add(file);
                        log.info("✅ Read file: {} ({} characters)", filePath.getFileName(), content.length());
                    } catch (IOException e) {
                        log.error("Failed to read file: " + filePath, e);
                    }
                });

        log.info("✅ Local Drive: Read {} files", files.size());
        return buildCombinedOutput(inputData, folderId, files);
    }

    private String buildCombinedOutput(String inputData, String folderId,
                                       List<Map<String, String>> files) throws Exception {
        JsonNode originalInput = null;
        if (inputData != null && inputData.trim().startsWith("{")) {
            try {
                originalInput = objectMapper.readTree(inputData.trim());
                log.info("📦 Preserving original input: {}", inputData.trim());
            } catch (Exception e) {
                log.warn("⚠️ Could not parse inputData as JSON, storing as raw string");
            }
        }

        Map<String, Object> fileData = new HashMap<>();
        fileData.put("totalFiles", files.size());
        fileData.put("folder", folderId);
        fileData.put("files", files);

        Map<String, Object> combined = new HashMap<>();
        if (originalInput != null) {
            combined.put("originalInput", originalInput);
        } else if (inputData != null) {
            combined.put("originalInput", inputData);
        }
        combined.put("fileData", fileData);

        String output = objectMapper.writeValueAsString(combined);
        log.info("📤 DRIVE output (combined) length: {} characters", output.length());
        return output;
    }

    /**
     * Extract text from file - supports both PDF and plain text files
     */
    private String extractTextFromFile(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            return extractTextFromPDF(filePath);
        } else {
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

        Path folderPath = Paths.get(storagePath, folderId);
        Files.createDirectories(folderPath);

        Path filePath = folderPath.resolve(fileName);
        try (FileWriter writer = new FileWriter(filePath.toFile())) {
            writer.write(inputData);
        }

        File file = filePath.toFile();
        long fileSize = file.length();
        log.info("✅ Local Drive: File saved - {} ({} bytes)", fileName, fileSize);

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