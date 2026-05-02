package ma.rh.ai.hr_workflow.integration.drive.service.Impl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ma.rh.ai.hr_workflow.integration.drive.DTOs.DriveResponseDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LocalDriveServiceImpl")
class LocalDriveServiceImplTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private LocalDriveServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

        service = new LocalDriveServiceImpl(objectMapper);
        // Point storagePath to the isolated temp directory for each test
        ReflectionTestUtils.setField(service, "storagePath", tempDir.toString());
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private String writeConfig(String action, String folderId, String fileName) {
        return String.format(
                "{\"action\":\"%s\",\"folderId\":\"%s\",\"fileName\":\"%s\"}",
                action, folderId, fileName);
    }

    private String writeConfigNoFile(String action, String folderId) {
        return String.format("{\"action\":\"%s\",\"folderId\":\"%s\"}", action, folderId);
    }

    private String readConfig(String folderId) {
        return String.format("{\"action\":\"read\",\"folderId\":\"%s\"}", folderId);
    }

    private DriveResponseDTO parseWriteResponse(String json) throws Exception {
        return objectMapper.readValue(json, DriveResponseDTO.class);
    }

    private Path createTextFile(Path folder, String name, String content) throws Exception {
        Files.createDirectories(folder);
        Path file = folder.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private Path createPdfFile(Path folder, String name, String text) throws Exception {
        Files.createDirectories(folder);
        Path pdfPath = folder.resolve(name);
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            doc.save(pdfPath.toFile());
        }
        return pdfPath;
    }

    // ─── action = "write" ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveFile() — action=write")
    class WriteAction {

        @Test
        @DisplayName("happy path — creates file with correct content and returns valid DriveResponseDTO JSON")
        void saveFile_write_happyPath() throws Exception {
            // Arrange
            String config    = writeConfig("write", "uploads", "report.txt");
            String inputData = "Hello, this is the file content.";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            DriveResponseDTO response = parseWriteResponse(result);
            assertThat(response.getFileId()).isEqualTo("report.txt");
            assertThat(response.getFileName()).isEqualTo("report.txt");
            assertThat(response.getFileUrl()).startsWith("file://");
            assertThat(response.getSize()).isPositive();
            assertThat(response.getCreatedAt()).isNotNull();

            // Verify file content on disk
            Path written = tempDir.resolve("uploads").resolve("report.txt");
            assertThat(written).exists();
            assertThat(Files.readString(written)).isEqualTo(inputData);
        }

        @Test
        @DisplayName("happy path — custom folderId creates nested directory")
        void saveFile_write_customFolderId_createsDirectory() throws Exception {
            // Arrange
            String config    = writeConfig("write", "cv_outputs", "analysis.txt");
            String inputData = "Analysis result text";

            // Act
            service.saveFile(config, inputData);

            // Assert
            Path folder = tempDir.resolve("cv_outputs");
            assertThat(folder).isDirectory();
            assertThat(folder.resolve("analysis.txt")).exists();
        }

        @Test
        @DisplayName("happy path — null fileName generates a timestamp-based filename")
        void saveFile_write_nullFileName_generatesName() throws Exception {
            // Arrange
            String config    = writeConfigNoFile("write", "output");
            String inputData = "Generated filename test";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            DriveResponseDTO response = parseWriteResponse(result);
            assertThat(response.getFileName()).startsWith("workflow_output_");
            assertThat(response.getFileName()).endsWith(".txt");

            Path folder = tempDir.resolve("output");
            long filesCount = Files.list(folder).count();
            assertThat(filesCount).isEqualTo(1);
        }

        @Test
        @DisplayName("happy path — null folderId defaults to 'default' subfolder")
        void saveFile_write_nullFolderId_usesDefaultFolder() throws Exception {
            // Arrange
            String config    = "{\"action\":\"write\",\"fileName\":\"test.txt\"}";
            String inputData = "Default folder test";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            DriveResponseDTO response = parseWriteResponse(result);
            Path defaultFolder = tempDir.resolve("default");
            assertThat(defaultFolder).isDirectory();
            assertThat(defaultFolder.resolve("test.txt")).exists();
        }

        @Test
        @DisplayName("happy path — default action when config has no action field is 'write'")
        void saveFile_noAction_defaultsToWrite() throws Exception {
            // Arrange
            String config    = "{\"folderId\":\"docs\",\"fileName\":\"out.txt\"}";
            String inputData = "Content";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            DriveResponseDTO response = parseWriteResponse(result);
            assertThat(response.getFileName()).isEqualTo("out.txt");
            assertThat(tempDir.resolve("docs").resolve("out.txt")).exists();
        }

        @Test
        @DisplayName("happy path — webViewLink is the absolute file path")
        void saveFile_write_webViewLinkIsAbsolutePath() throws Exception {
            // Arrange
            String config    = writeConfig("write", "test_folder", "link_test.txt");
            String inputData = "Some content";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            DriveResponseDTO response = parseWriteResponse(result);
            Path expectedPath = tempDir.resolve("test_folder").resolve("link_test.txt");
            assertThat(response.getWebViewLink()).isEqualTo(expectedPath.toAbsolutePath().toString());
        }

        @Test
        @DisplayName("happy path — fileSize matches bytes written to disk")
        void saveFile_write_fileSizeMatchesDiskSize() throws Exception {
            // Arrange
            String content   = "Exactly 30 bytes of text data!!";
            String config    = writeConfig("write", "size_test", "sized.txt");

            // Act
            String result = service.saveFile(config, content);

            // Assert
            DriveResponseDTO response = parseWriteResponse(result);
            Path written = tempDir.resolve("size_test").resolve("sized.txt");
            assertThat(response.getSize()).isEqualTo(Files.size(written));
        }

        @Test
        @DisplayName("exception — invalid configJson throws RuntimeException wrapping parse error")
        void saveFile_write_invalidConfig_throws() {
            // Arrange
            String brokenConfig = "{not valid json";
            String inputData    = "data";

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.saveFile(brokenConfig, inputData));
            assertThat(ex.getMessage()).contains("Local Drive operation failed");
        }
    }

    // ─── action = "read" — text files ─────────────────────────────────────────────

    @Nested
    @DisplayName("saveFile() — action=read (text files)")
    class ReadActionTextFiles {

        @Test
        @DisplayName("happy path — reads all text files from folder and returns combined JSON")
        void saveFile_read_textFiles_returnsCombinedJson() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("cv_uploads");
            createTextFile(folder, "alice.txt", "Alice has 5 years Java experience");
            createTextFile(folder, "bob.txt", "Bob has 2 years Python experience");

            String config    = readConfig("cv_uploads");
            String inputData = "{\"prompt\":\"Rank top candidates\"}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            @SuppressWarnings("unchecked")
            Map<String, Object> combined = objectMapper.readValue(result, Map.class);
            assertThat(combined).containsKey("originalInput");
            assertThat(combined).containsKey("cvData");

            @SuppressWarnings("unchecked")
            Map<String, Object> cvData = (Map<String, Object>) combined.get("cvData");
            assertThat(cvData.get("totalCVs")).isEqualTo(2);
            assertThat(cvData.get("folder")).isEqualTo("cv_uploads");

            var cvs = (java.util.List<?>) cvData.get("cvs");
            assertThat(cvs).hasSize(2);
        }

        @Test
        @DisplayName("happy path — file contents are included in each CV entry")
        void saveFile_read_fileContentsIncluded() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("docs");
            createTextFile(folder, "candidate.txt", "Senior Java Developer with 7 years");

            String config    = readConfig("docs");
            String inputData = "{\"prompt\":\"Evaluate\"}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            Map<?, ?> combined = objectMapper.readValue(result, Map.class);
            Map<?, ?> cvData   = (Map<?, ?>) combined.get("cvData");
            var cvs = (java.util.List<Map<?, ?>>) cvData.get("cvs");

            assertThat(cvs).hasSize(1);
            Map<?, ?> cv = cvs.get(0);
            assertThat(cv.get("fileName")).isEqualTo("candidate.txt");
            assertThat(cv.get("content").toString()).contains("Senior Java Developer");
        }

        @Test
        @DisplayName("happy path — empty folder returns combined JSON with totalCVs=0")
        void saveFile_read_emptyFolder_zeroTotal() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("empty_folder");
            Files.createDirectories(folder);

            String config    = readConfig("empty_folder");
            String inputData = "{\"prompt\":\"Find candidates\"}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            Map<?, ?> combined = objectMapper.readValue(result, Map.class);
            Map<?, ?> cvData   = (Map<?, ?>) combined.get("cvData");
            assertThat(cvData.get("totalCVs")).isEqualTo(0);
            assertThat((java.util.List<?>) cvData.get("cvs")).isEmpty();
        }

        @Test
        @DisplayName("happy path — JSON inputData is preserved as parsed originalInput object")
        void saveFile_read_jsonInputData_preservedAsOriginalInput() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("prefs");
            createTextFile(folder, "cv.txt", "Alice Senior Dev");

            String config    = readConfig("prefs");
            String inputData = "{\"prompt\":\"Top 5\",\"minExperience\":3}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            Map<?, ?> combined     = objectMapper.readValue(result, Map.class);
            Map<?, ?> originalInput = (Map<?, ?>) combined.get("originalInput");
            assertThat(originalInput.get("prompt")).isEqualTo("Top 5");
            assertThat(originalInput.get("minExperience")).isEqualTo(3);
        }

        @Test
        @DisplayName("happy path — non-JSON inputData is stored as raw string in originalInput")
        void saveFile_read_rawStringInputData_storedAsRawOriginalInput() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("raw_input");
            createTextFile(folder, "cv.txt", "Bob Junior");

            String config    = readConfig("raw_input");
            String inputData = "just a plain string criteria";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            Map<?, ?> combined = objectMapper.readValue(result, Map.class);
            assertThat(combined.get("originalInput")).isEqualTo(inputData);
        }

        @Test
        @DisplayName("happy path — null inputData produces combined JSON without originalInput")
        void saveFile_read_nullInputData_noOriginalInput() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("no_input");
            createTextFile(folder, "cv.txt", "Carol Mid Dev");

            String config = readConfig("no_input");

            // Act
            String result = service.saveFile(config, null);

            // Assert
            @SuppressWarnings("unchecked")
            Map<String, Object> combined = objectMapper.readValue(result, Map.class);
            assertThat(combined).doesNotContainKey("originalInput");
            assertThat(combined).containsKey("cvData");
        }

        @Test
        @DisplayName("happy path — null folderId defaults to 'default' folder")
        void saveFile_read_nullFolderId_usesDefaultFolder() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("default");
            createTextFile(folder, "cv.txt", "Default folder test");

            String config    = "{\"action\":\"read\"}";
            String inputData = "{\"prompt\":\"Test\"}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            Map<?, ?> combined = objectMapper.readValue(result, Map.class);
            Map<?, ?> cvData   = (Map<?, ?>) combined.get("cvData");
            assertThat(cvData.get("folder")).isEqualTo("default");
            assertThat(cvData.get("totalCVs")).isEqualTo(1);
        }

        @Test
        @DisplayName("exception — folder does not exist throws RuntimeException")
        void saveFile_read_folderNotFound_throws() {
            // Arrange
            String config    = readConfig("nonexistent_folder");
            String inputData = "{\"prompt\":\"Test\"}";

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.saveFile(config, inputData));
            assertThat(ex.getMessage()).contains("Local Drive operation failed");
        }

        @Test
        @DisplayName("files are read in sorted (consistent) order")
        void saveFile_read_filesAreSorted() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("sorted");
            createTextFile(folder, "zzz_last.txt", "Last");
            createTextFile(folder, "aaa_first.txt", "First");
            createTextFile(folder, "mmm_middle.txt", "Middle");

            String config    = readConfig("sorted");
            String inputData = "{\"prompt\":\"Sort test\"}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert — cvs should be in alphabetical order
            Map<?, ?> combined = objectMapper.readValue(result, Map.class);
            var cvs = (java.util.List<Map<?, ?>>) ((Map<?, ?>) combined.get("cvData")).get("cvs");
            assertThat(cvs).hasSize(3);
            assertThat(cvs.get(0).get("fileName")).isEqualTo("aaa_first.txt");
            assertThat(cvs.get(1).get("fileName")).isEqualTo("mmm_middle.txt");
            assertThat(cvs.get(2).get("fileName")).isEqualTo("zzz_last.txt");
        }
    }

    // ─── action = "read" — PDF files ──────────────────────────────────────────────

    @Nested
    @DisplayName("saveFile() — action=read (PDF files)")
    class ReadActionPdfFiles {

        @Test
        @DisplayName("happy path — PDF file is extracted and content included in CV entry")
        void saveFile_read_pdfFile_extractsText() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("pdf_folder");
            createPdfFile(folder, "resume.pdf", "Jane Smith Senior Developer");

            String config    = readConfig("pdf_folder");
            String inputData = "{\"prompt\":\"Evaluate PDF resume\"}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            Map<?, ?> combined = objectMapper.readValue(result, Map.class);
            Map<?, ?> cvData   = (Map<?, ?>) combined.get("cvData");
            assertThat(cvData.get("totalCVs")).isEqualTo(1);

            var cvs = (java.util.List<Map<?, ?>>) cvData.get("cvs");
            assertThat(cvs.get(0).get("fileName")).isEqualTo("resume.pdf");
            assertThat(cvs.get(0).get("content").toString()).contains("Jane Smith");
        }

        @Test
        @DisplayName("happy path — mixed text and PDF files are both processed")
        void saveFile_read_mixedFiles_bothProcessed() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("mixed");
            createTextFile(folder, "alice.txt", "Alice text CV");
            createPdfFile(folder, "bob.pdf", "Bob PDF Resume");

            String config    = readConfig("mixed");
            String inputData = "{\"prompt\":\"Mixed files\"}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert
            Map<?, ?> combined = objectMapper.readValue(result, Map.class);
            Map<?, ?> cvData   = (Map<?, ?>) combined.get("cvData");
            assertThat(cvData.get("totalCVs")).isEqualTo(2);
        }
    }

    // ─── action = "READ" (uppercase) ─────────────────────────────────────────────

    @Nested
    @DisplayName("saveFile() — action case insensitivity")
    class ActionCaseInsensitivity {

        @Test
        @DisplayName("action 'READ' (uppercase) is treated as read")
        void saveFile_uppercaseReadAction_treatedAsRead() throws Exception {
            // Arrange
            Path folder = tempDir.resolve("case_test");
            createTextFile(folder, "cv.txt", "Carol Dev");

            String config    = "{\"action\":\"READ\",\"folderId\":\"case_test\"}";
            String inputData = "{\"prompt\":\"Test\"}";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert — should be read response (has cvData), not write response
            @SuppressWarnings("unchecked")
            Map<String, Object> combined = objectMapper.readValue(result, Map.class);
            assertThat(combined).containsKey("cvData");
        }

        @Test
        @DisplayName("action 'WRITE' (uppercase) defaults to write path")
        void saveFile_uppercaseWriteAction_treatedAsWrite() throws Exception {
            // Arrange
            String config    = writeConfig("WRITE", "wr_test", "output.txt");
            String inputData = "Uppercase write test";

            // Act
            String result = service.saveFile(config, inputData);

            // Assert — should be DriveResponseDTO (has fileId)
            DriveResponseDTO response = parseWriteResponse(result);
            assertThat(response.getFileId()).isEqualTo("output.txt");
        }
    }
}
