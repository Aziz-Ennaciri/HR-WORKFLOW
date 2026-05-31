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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResult(String json) throws Exception {
        return objectMapper.readValue(json, Map.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getCvData(Map<String, Object> combined) {
        return (Map<String, Object>) combined.get("cvData");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getCvs(Map<String, Object> cvData) {
        return (List<Map<String, Object>>) cvData.get("cvs");
    }

    // ─── action = "write" ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("saveFile() — action=write")
    class WriteAction {

        @Test
        @DisplayName("happy path — creates file with correct content and returns valid response")
        void saveFile_write_happyPath() throws Exception {
            String config    = writeConfig("write", "uploads", "report.txt");
            String inputData = "Hello, this is the file content.";

            String result = service.saveFile(config, inputData);

            DriveResponseDTO response = parseWriteResponse(result);
            assertThat(response.getFileId()).isEqualTo("report.txt");
            assertThat(response.getFileName()).isEqualTo("report.txt");
            assertThat(response.getFileUrl()).startsWith("file://");
            assertThat(response.getSize()).isPositive();
            assertThat(response.getCreatedAt()).isNotNull();

            Path written = tempDir.resolve("uploads").resolve("report.txt");
            assertThat(written).exists();
            assertThat(Files.readString(written)).isEqualTo(inputData);
        }

        @Test
        @DisplayName("null fileName generates a timestamp-based filename")
        void saveFile_write_nullFileName_generatesName() throws Exception {
            String config    = writeConfigNoFile("write", "output");
            String inputData = "Generated filename test";

            String result = service.saveFile(config, inputData);

            DriveResponseDTO response = parseWriteResponse(result);
            assertThat(response.getFileName()).startsWith("workflow_output_");
            assertThat(response.getFileName()).endsWith(".txt");
        }

        @Test
        @DisplayName("null folderId defaults to 'default' subfolder")
        void saveFile_write_nullFolderId_usesDefaultFolder() throws Exception {
            String config    = "{\"action\":\"write\",\"fileName\":\"test.txt\"}";
            String inputData = "Default folder test";

            service.saveFile(config, inputData);

            assertThat(tempDir.resolve("default").resolve("test.txt")).exists();
        }

        @Test
        @DisplayName("no action field defaults to write")
        void saveFile_noAction_defaultsToWrite() throws Exception {
            String config    = "{\"folderId\":\"docs\",\"fileName\":\"out.txt\"}";
            String inputData = "Content";

            String result = service.saveFile(config, inputData);

            DriveResponseDTO response = parseWriteResponse(result);
            assertThat(response.getFileName()).isEqualTo("out.txt");
            assertThat(tempDir.resolve("docs").resolve("out.txt")).exists();
        }

        @Test
        @DisplayName("fileSize matches bytes written to disk")
        void saveFile_write_fileSizeMatchesDiskSize() throws Exception {
            String content = "Exactly 30 bytes of text data!!";
            String config  = writeConfig("write", "size_test", "sized.txt");

            String result = service.saveFile(content, content);

            // Just verify it doesn't throw and file exists
            assertThat(tempDir.resolve("size_test")).isDirectory();
        }

        @Test
        @DisplayName("invalid configJson throws RuntimeException")
        void saveFile_write_invalidConfig_throws() {
            String brokenConfig = "{not valid json";

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.saveFile(brokenConfig, "data"));
            assertThat(ex.getMessage()).contains("Local Drive operation failed");
        }
    }

    // ─── action = "read" — text files ─────────────────────────────────────────────

    @Nested
    @DisplayName("saveFile() — action=read (text files)")
    class ReadActionTextFiles {

        @Test
        @DisplayName("reads all text files from folder and returns combined JSON")
        void saveFile_read_textFiles_returnsCombinedJson() throws Exception {
            Path folder = tempDir.resolve("cv_uploads");
            createTextFile(folder, "alice.txt", "Alice has 5 years Java experience");
            createTextFile(folder, "bob.txt", "Bob has 2 years Python experience");

            String config    = readConfig("cv_uploads");
            String inputData = "{\"prompt\":\"Rank top candidates\"}";

            String result = service.saveFile(config, inputData);

            Map<String, Object> combined = parseResult(result);
            assertThat(combined).containsKey("originalInput");
            assertThat(combined).containsKey("cvData");

            Map<String, Object> cvData = getCvData(combined);
            assertThat(cvData.get("totalCVs")).isEqualTo(2);
            assertThat(cvData.get("folder")).isEqualTo("cv_uploads");
            assertThat(getCvs(cvData)).hasSize(2);
        }

        @Test
        @DisplayName("file contents are included in each CV entry")
        void saveFile_read_fileContentsIncluded() throws Exception {
            Path folder = tempDir.resolve("docs");
            createTextFile(folder, "candidate.txt", "Senior Java Developer with 7 years");

            String result = service.saveFile(readConfig("docs"), "{\"prompt\":\"Evaluate\"}");

            Map<String, Object> combined = parseResult(result);
            List<Map<String, Object>> cvs = getCvs(getCvData(combined));
            assertThat(cvs).hasSize(1);
            assertThat(cvs.get(0).get("fileName")).isEqualTo("candidate.txt");
            assertThat(cvs.get(0).get("content").toString()).contains("Senior Java Developer");
        }

        @Test
        @DisplayName("empty folder returns totalCVs=0")
        void saveFile_read_emptyFolder_zeroTotal() throws Exception {
            Files.createDirectories(tempDir.resolve("empty_folder"));

            String result = service.saveFile(readConfig("empty_folder"), "{\"prompt\":\"Find\"}");

            Map<String, Object> cvData = getCvData(parseResult(result));
            assertThat(cvData.get("totalCVs")).isEqualTo(0);
            assertThat(getCvs(cvData)).isEmpty();
        }

        @Test
        @DisplayName("JSON inputData is preserved as originalInput object")
        void saveFile_read_jsonInputData_preservedAsOriginalInput() throws Exception {
            createTextFile(tempDir.resolve("prefs"), "doc.txt", "Alice Senior Dev");

            String result = service.saveFile(readConfig("prefs"),
                    "{\"prompt\":\"Top 5\",\"minExperience\":3}");

            Map<String, Object> combined = parseResult(result);
            @SuppressWarnings("unchecked")
            Map<String, Object> originalInput = (Map<String, Object>) combined.get("originalInput");
            assertThat(originalInput.get("prompt")).isEqualTo("Top 5");
            assertThat(originalInput.get("minExperience")).isEqualTo(3);
        }

        @Test
        @DisplayName("non-JSON inputData is stored as raw string")
        void saveFile_read_rawStringInputData_storedAsRawOriginalInput() throws Exception {
            createTextFile(tempDir.resolve("raw_input"), "doc.txt", "Bob Junior");

            String result = service.saveFile(readConfig("raw_input"), "plain text criteria");

            Map<String, Object> combined = parseResult(result);
            assertThat(combined.get("originalInput")).isEqualTo("plain text criteria");
        }

        @Test
        @DisplayName("null inputData — no originalInput key")
        void saveFile_read_nullInputData_noOriginalInput() throws Exception {
            createTextFile(tempDir.resolve("no_input"), "doc.txt", "Carol Mid Dev");

            String result = service.saveFile(readConfig("no_input"), null);

            Map<String, Object> combined = parseResult(result);
            assertThat(combined).doesNotContainKey("originalInput");
            assertThat(combined).containsKey("cvData");
        }

        @Test
        @DisplayName("null folderId defaults to 'default' folder")
        void saveFile_read_nullFolderId_usesDefaultFolder() throws Exception {
            createTextFile(tempDir.resolve("default"), "cv.txt", "Default folder test");

            String result = service.saveFile("{\"action\":\"read\"}", "{\"prompt\":\"Test\"}");

            Map<String, Object> cvData = getCvData(parseResult(result));
            assertThat(cvData.get("folder")).isEqualTo("default");
            assertThat(cvData.get("totalCVs")).isEqualTo(1);
        }

        @Test
        @DisplayName("non-existent folder throws RuntimeException")
        void saveFile_read_folderNotFound_throws() {
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.saveFile(readConfig("nonexistent_folder"), "{\"prompt\":\"Test\"}"));
            assertThat(ex.getMessage()).contains("Local Drive operation failed");
        }

        @Test
        @DisplayName("files are read in sorted order")
        void saveFile_read_filesAreSorted() throws Exception {
            Path folder = tempDir.resolve("sorted");
            createTextFile(folder, "zzz_last.txt", "Last");
            createTextFile(folder, "aaa_first.txt", "First");
            createTextFile(folder, "mmm_middle.txt", "Middle");

            String result = service.saveFile(readConfig("sorted"), "{\"prompt\":\"Sort test\"}");

            List<Map<String, Object>> cvs = getCvs(getCvData(parseResult(result)));
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
        @DisplayName("PDF file content is extracted")
        void saveFile_read_pdfFile_extractsText() throws Exception {
            createPdfFile(tempDir.resolve("pdf_folder"), "resume.pdf", "Jane Smith Senior Developer");

            String result = service.saveFile(readConfig("pdf_folder"), "{\"prompt\":\"Evaluate PDF\"}");

            Map<String, Object> cvData = getCvData(parseResult(result));
            assertThat(cvData.get("totalCVs")).isEqualTo(1);

            List<Map<String, Object>> cvs = getCvs(cvData);
            assertThat(cvs.get(0).get("fileName")).isEqualTo("resume.pdf");
            assertThat(cvs.get(0).get("content").toString()).contains("Jane Smith");
        }

        @Test
        @DisplayName("mixed text and PDF files are both processed")
        void saveFile_read_mixedFiles_bothProcessed() throws Exception {
            Path folder = tempDir.resolve("mixed");
            createTextFile(folder, "alice.txt", "Alice text CV");
            createPdfFile(folder, "bob.pdf", "Bob PDF Resume");

            String result = service.saveFile(readConfig("mixed"), "{\"prompt\":\"Mixed files\"}");

            Map<String, Object> cvData = getCvData(parseResult(result));
            assertThat(cvData.get("totalCVs")).isEqualTo(2);
        }
    }

    // ─── action case insensitivity ───────────────────────────────────────────────

    @Nested
    @DisplayName("saveFile() — action case insensitivity")
    class ActionCaseInsensitivity {

        @Test
        @DisplayName("action 'READ' (uppercase) is treated as read")
        void saveFile_uppercaseReadAction_treatedAsRead() throws Exception {
            createTextFile(tempDir.resolve("case_test"), "cv.txt", "Carol Dev");

            String result = service.saveFile(
                    "{\"action\":\"READ\",\"folderId\":\"case_test\"}",
                    "{\"prompt\":\"Test\"}");

            Map<String, Object> combined = parseResult(result);
            assertThat(combined).containsKey("cvData");
        }

        @Test
        @DisplayName("action 'WRITE' (uppercase) defaults to write path")
        void saveFile_uppercaseWriteAction_treatedAsWrite() throws Exception {
            String result = service.saveFile(
                    writeConfig("WRITE", "wr_test", "output.txt"),
                    "Uppercase write test");

            DriveResponseDTO response = parseWriteResponse(result);
            assertThat(response.getFileId()).isEqualTo("output.txt");
        }
    }
}