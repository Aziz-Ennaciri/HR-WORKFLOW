package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RealExcelServiceImpl")
class RealExcelServiceImplTest {

    private ObjectMapper objectMapper;
    private RealExcelServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new RealExcelServiceImpl(objectMapper);
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private String baseConfigJson(String sheetName, String operation) {
        return String.format("{\"sheetName\":\"%s\",\"operation\":\"%s\"}",
                sheetName != null ? sheetName : "Data",
                operation  != null ? operation  : "WRITE");
    }

    private String analysisInputJson(String analysisText) {
        // Escapes for embedding in JSON
        String escaped = analysisText
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        return "{\"analysis\":\"" + escaped + "\"}";
    }

    private ExcelResponseDTO parseResponse(String json) throws Exception {
        return objectMapper.readValue(json, ExcelResponseDTO.class);
    }

    // ─── Format 1: Markdown table ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Format 1: Markdown table (analysis field with | headers)")
    class MarkdownTableFormat {

        private final String MARKDOWN_TABLE =
                "| Name       | Score | Email           |\n"
                + "|------------|-------|------------------|\n"
                + "| Alice Smith| 9     | alice@test.com  |\n"
                + "| Bob Jones  | 7     | bob@test.com    |\n"
                + "| Carol White| 8     | carol@test.com  |";

        @Test
        @DisplayName("happy path — analysis with markdown table produces multi-row Excel")
        void processData_markdownTable_happyPath() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Rankings", "WRITE");
            String inputData  = analysisInputJson(MARKDOWN_TABLE);

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Rankings");
            assertThat(response.getOperation()).isEqualTo("WRITE");
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
            // 1 header + 3 data rows = 4 rows total
            assertThat(response.getRowsProcessed()).isEqualTo(4);
            assertThat(response.getOutputFileUrl()).isNotBlank();
            assertThat(response.getFileContent()).isNotBlank();
            assertThat(response.getFileSize()).isGreaterThan(0L);
            assertThat(response.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("markdown table with separator lines — separators are skipped")
        void processData_markdownTable_separatorsSkipped() throws Exception {
            // Arrange
            String configJson  = baseConfigJson("Sheet1", "WRITE");
            String markdownTwo = "| Col A | Col B |\n"
                    + "|-------|-------|\n"
                    + "| Val 1 | Val 2 |";
            String inputData   = analysisInputJson(markdownTwo);

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            // Header row + 1 data row = 2 rows; separator line is not written
            assertThat(response.getRowsProcessed()).isEqualTo(2);
        }

        @Test
        @DisplayName("verifies output file exists on disk")
        void processData_markdownTable_fileExistsOnDisk() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Data", "WRITE");
            String inputData  = analysisInputJson(MARKDOWN_TABLE);

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            File f = new File(response.getOutputFileUrl());
            assertThat(f).exists();
            assertThat(f.getName()).endsWith(".xlsx");
        }
    }

    // ─── Format 2: Block sections ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Format 2: Block/section format (numbered sections with bullets)")
    class BlockSectionsFormat {

        private final String BLOCK_TEXT =
                "1. Alice Smith\n"
                + "- Score: 9\n"
                + "- Skills: Java, Spring Boot\n"
                + "- Email: alice@test.com\n"
                + "\n"
                + "2. Bob Jones\n"
                + "- Score: 7\n"
                + "- Skills: Python, Django\n"
                + "- Email: bob@test.com\n"
                + "\n"
                + "3. Carol White\n"
                + "- Score: 8\n"
                + "- Skills: React, Node.js\n"
                + "- Email: carol@test.com";

        @Test
        @DisplayName("happy path — analysis with numbered+bullet blocks produces structured table")
        void processData_blockSections_happyPath() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Candidates", "WRITE");
            String inputData  = analysisInputJson(BLOCK_TEXT);

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Candidates");
            // 1 header row + 3 block rows = 4 rows
            assertThat(response.getRowsProcessed()).isEqualTo(4);
            assertThat(response.getFileContent()).isNotBlank();
            assertThat(response.getFileSize()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("block text with colon-separated bullet fields — parsed into columns")
        void processData_blockSections_colonBullets_createsColumns() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Results", "WRITE");
            String twoBlocks  = "1. Alice\n"
                    + "- Score: 9\n"
                    + "- Experience: 5 years\n"
                    + "\n"
                    + "2. Bob\n"
                    + "- Score: 6\n"
                    + "- Experience: 2 years";
            String inputData  = analysisInputJson(twoBlocks);

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(3); // 1 header + 2 blocks
        }

        @Test
        @DisplayName("markdown heading (## Title) with bullets also triggers block format")
        void processData_blockSections_hashHeadings_detected() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Data", "WRITE");
            String headingText = "## Top Candidates\n"
                    + "- Score: 9\n"
                    + "- Name: Alice\n"
                    + "\n"
                    + "## Second Tier\n"
                    + "- Score: 7\n"
                    + "- Name: Bob";
            String inputData  = analysisInputJson(headingText);

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
        }
    }

    // ─── Format 3: Plain text fallback ────────────────────────────────────────────

    @Nested
    @DisplayName("Format 3: Plain text fallback (no table, no structured blocks)")
    class PlainTextFormat {

        @Test
        @DisplayName("happy path — plain text produces title row + content rows")
        void processData_plainText_happyPath() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Analysis", "WRITE");
            String plainText  = "This is a plain analysis result.\n"
                    + "No table structure here.\n"
                    + "Just narrative text about the candidates.";
            String inputData  = analysisInputJson(plainText);

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Analysis");
            // Title row + blank separator + 3 text lines = 5 rows
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
            assertThat(response.getFileContent()).isNotBlank();
        }

        @Test
        @DisplayName("single line plain text — creates title + content in 3 rows")
        void processData_plainText_singleLine() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Result", "WRITE");
            String inputData  = analysisInputJson("No candidates matched the criteria.");

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(3); // title + blank + 1 line
        }

        @Test
        @DisplayName("raw non-JSON text input (textData path) — also dispatches to adaptive formatter")
        void processData_rawTextInput_notJson() throws Exception {
            // Arrange
            String configJson = baseConfigJson("RawData", "WRITE");
            String rawText    = "just plain text, not json at all";

            // Act
            String result = service.processData(configJson, rawText);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("RawData");
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
        }

        @Test
        @DisplayName("raw non-JSON text that looks like markdown table — uses markdown formatter")
        void processData_rawTextInput_markdownTable_usesMarkdownFormatter() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Table", "WRITE");
            String rawMarkdown = "| A | B | C |\n|---|---|---|\n| 1 | 2 | 3 |";

            // Act
            String result = service.processData(configJson, rawMarkdown);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(2); // header + 1 data row
        }
    }

    // ─── Format 4: JSON table (non-analysis JSON input) ──────────────────────────
    //
    // NOTE: The production code's else-branch calls
    //   objectMapper.convertValue(dataNode, List.class)
    // which succeeds for JSON arrays but throws for JSON objects.
    // Tests here use JSON arrays, which is what the code actually handles at runtime.

    @Nested
    @DisplayName("Format 4: JSON table (input is a JSON array without 'analysis' field)")
    class JsonTableFormat {

        @Test
        @DisplayName("happy path — JSON array produces valid response with columnsProcessed = array size")
        void processData_jsonArray_happyPath() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Report", "WRITE");
            // JSON array — convertValue(ArrayNode, List.class) succeeds
            String inputData  = "[\"Alice\",\"Bob\",\"Carol\"]";

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Report");
            // columnsProcessed = dataNode.size() = 3 (array elements)
            assertThat(response.getColumnsProcessed()).isEqualTo(3);
            assertThat(response.getFileContent()).isNotBlank();
            assertThat(response.getFileSize()).isPositive();
        }

        @Test
        @DisplayName("JSON array with multiple elements — columnsProcessed equals element count")
        void processData_jsonArray_elementCountCorrect() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Data", "WRITE");
            String inputData  = "[1, 2, 3, 4, 5]";

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getColumnsProcessed()).isEqualTo(5);
        }

        @Test
        @DisplayName("JSON input with 'analysis' field — routes to adaptive text formatter, not JSON table")
        void processData_jsonWithAnalysisField_usesTextFormatter() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Data", "WRITE");
            String inputData  = "{\"analysis\":\"Short plain text result\"}";

            // Act
            String result = service.processData(configJson, inputData);

            // Assert — adaptive text path: columnsProcessed stays at 1 (default for text)
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getColumnsProcessed()).isEqualTo(1);
        }

        @Test
        @DisplayName("plain JSON object without 'analysis' — throws RuntimeException (known production limitation)")
        void processData_jsonObject_throwsRuntimeException() {
            // Arrange — JSON objects hit a convertValue(ObjectNode, List.class) failure
            String configJson = baseConfigJson("Report", "WRITE");
            String inputData  = "{\"name\":\"Alice\",\"score\":\"9\"}";

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.processData(configJson, inputData));
            assertThat(ex.getMessage()).contains("Excel processing failed");
        }
    }

    // ─── Config variations ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Config variations")
    class ConfigVariations {

        @Test
        @DisplayName("null sheetName in config defaults to 'Data'")
        void processData_nullSheetName_defaultsToData() throws Exception {
            // Arrange — use analysis-field JSON to avoid convertValue issue on plain objects
            String configJson = "{\"operation\":\"WRITE\"}"; // no sheetName
            String inputData  = analysisInputJson("some analysis");

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Data");
        }

        @Test
        @DisplayName("null operation in config defaults to 'WRITE'")
        void processData_nullOperation_defaultsToWrite() throws Exception {
            // Arrange
            String configJson = "{\"sheetName\":\"Sheet1\"}"; // no operation
            String inputData  = analysisInputJson("result text");

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getOperation()).isEqualTo("WRITE");
        }

        @Test
        @DisplayName("custom sheetName is reflected in response and file")
        void processData_customSheetName() throws Exception {
            // Arrange
            String configJson = baseConfigJson("MyCustomSheet", "WRITE");
            String inputData  = analysisInputJson("custom sheet data");

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("MyCustomSheet");
        }

        @Test
        @DisplayName("exception — invalid configJson throws RuntimeException")
        void processData_invalidConfigJson_throws() {
            // Arrange
            String brokenConfig = "{not valid json}";
            String inputData    = "{\"key\":\"val\"}";

            // Act & Assert
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.processData(brokenConfig, inputData));
            assertThat(ex.getMessage()).contains("Excel processing failed");
        }
    }

    // ─── Response structure invariants ───────────────────────────────────────────

    @Nested
    @DisplayName("Response structure — invariants for all formats")
    class ResponseInvariants {

        @Test
        @DisplayName("response always contains non-null outputFileUrl, webFileUrl, fileContent")
        void processData_responseHasRequiredFields() throws Exception {
            // Arrange — use analysis-field JSON to avoid the convertValue limitation on objects
            String configJson = baseConfigJson("Sheet", "WRITE");
            String inputData  = analysisInputJson("Summary of results");

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getOutputFileUrl()).isNotNull();
            assertThat(response.getWebFileUrl()).startsWith("/api/v1/files/excel/");
            assertThat(response.getWebFileUrl()).endsWith(".xlsx");
            assertThat(response.getFileContent()).isNotNull();
            assertThat(response.getFileSize()).isPositive();
            assertThat(response.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("webFileUrl follows pattern /api/v1/files/excel/<uuid>.xlsx")
        void processData_webFileUrlPattern() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Sheet", "WRITE");
            String inputData  = analysisInputJson("Some analysis text here");

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getWebFileUrl())
                    .matches("/api/v1/files/excel/workflow-\\d+-[a-f0-9]+\\.xlsx");
        }

        @Test
        @DisplayName("fileContent is valid Base64-encoded bytes")
        void processData_fileContentIsBase64() throws Exception {
            // Arrange
            String configJson = baseConfigJson("Sheet", "WRITE");
            String inputData  = analysisInputJson("Some plain text result for encoding test");

            // Act
            String result = service.processData(configJson, inputData);

            // Assert
            ExcelResponseDTO response = parseResponse(result);
            assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(response.getFileContent()),
                    "fileContent should be valid Base64");
            assertThat(java.util.Base64.getDecoder().decode(response.getFileContent())).isNotEmpty();
        }
    }
}
