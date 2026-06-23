package ma.rh.ai.hr_workflow.integration.excel.service.Impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelResponseDTO;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RealExcelServiceImpl")
class RealExcelServiceImplTest {

    private ObjectMapper objectMapper;
    private RealExcelServiceImpl service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new RealExcelServiceImpl(objectMapper);
        ReflectionTestUtils.setField(service, "storagePath", tempDir.toString());
    }


    private String baseConfigJson(String sheetName, String operation) {
        return String.format("{\"sheetName\":\"%s\",\"operation\":\"%s\"}",
                sheetName != null ? sheetName : "Data",
                operation  != null ? operation  : "WRITE");
    }

    private String analysisInputJson(String analysisText) {
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
            String configJson = baseConfigJson("Rankings", "WRITE");
            String inputData  = analysisInputJson(MARKDOWN_TABLE);
            
            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Rankings");
            assertThat(response.getOperation()).isEqualTo("WRITE");
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
            assertThat(response.getRowsProcessed()).isEqualTo(4);
            assertThat(response.getOutputFileUrl()).isNotBlank();
            assertThat(response.getFileContent()).isNotBlank();
            assertThat(response.getFileSize()).isGreaterThan(0L);
            assertThat(response.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("markdown table with separator lines — separators are skipped")
        void processData_markdownTable_separatorsSkipped() throws Exception {
            String configJson  = baseConfigJson("Sheet1", "WRITE");
            String markdownTwo = "| Col A | Col B |\n"
                    + "|-------|-------|\n"
                    + "| Val 1 | Val 2 |";
            String inputData   = analysisInputJson(markdownTwo);

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(2);
        }

        @Test
        @DisplayName("verifies output file exists on disk")
        void processData_markdownTable_fileExistsOnDisk() throws Exception {
            String configJson = baseConfigJson("Data", "WRITE");
            String inputData  = analysisInputJson(MARKDOWN_TABLE);

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            File f = new File(response.getOutputFileUrl());
            assertThat(f).exists();
            assertThat(f.getName()).endsWith(".xlsx");
        }
    }


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
            String configJson = baseConfigJson("Candidates", "WRITE");
            String inputData  = analysisInputJson(BLOCK_TEXT);

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Candidates");
            assertThat(response.getRowsProcessed()).isEqualTo(4);
            assertThat(response.getFileContent()).isNotBlank();
            assertThat(response.getFileSize()).isGreaterThan(0L);
        }

        @Test
        @DisplayName("block text with colon-separated bullet fields — parsed into columns")
        void processData_blockSections_colonBullets_createsColumns() throws Exception {
            String configJson = baseConfigJson("Results", "WRITE");
            String twoBlocks  = "1. Alice\n"
                    + "- Score: 9\n"
                    + "- Experience: 5 years\n"
                    + "\n"
                    + "2. Bob\n"
                    + "- Score: 6\n"
                    + "- Experience: 2 years";
            String inputData  = analysisInputJson(twoBlocks);

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(3);
        }

        @Test
        @DisplayName("markdown heading (## Title) with bullets also triggers block format")
        void processData_blockSections_hashHeadings_detected() throws Exception {
            String configJson = baseConfigJson("Data", "WRITE");
            String headingText = "## Top Candidates\n"
                    + "- Score: 9\n"
                    + "- Name: Alice\n"
                    + "\n"
                    + "## Second Tier\n"
                    + "- Score: 7\n"
                    + "- Name: Bob";
            String inputData  = analysisInputJson(headingText);

            String result = service.processData(configJson, inputData);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
        }
    }


    @Nested
    @DisplayName("Format 3: Plain text fallback (no table, no structured blocks)")
    class PlainTextFormat {

        @Test
        @DisplayName("happy path — plain text produces title row + content rows")
        void processData_plainText_happyPath() throws Exception {
            String configJson = baseConfigJson("Analysis", "WRITE");
            String plainText  = "This is a plain analysis result.\n"
                    + "No table structure here.\n"
                    + "Just narrative text about the candidates.";
            String inputData  = analysisInputJson(plainText);

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Analysis");
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
            assertThat(response.getFileContent()).isNotBlank();
        }

        @Test
        @DisplayName("single line plain text — creates title + content in 3 rows")
        void processData_plainText_singleLine() throws Exception {
            String configJson = baseConfigJson("Result", "WRITE");
            String inputData  = analysisInputJson("No candidates matched the criteria.");

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(3);
        }

        @Test
        @DisplayName("raw non-JSON text input (textData path) — also dispatches to adaptive formatter")
        void processData_rawTextInput_notJson() throws Exception {
             
            String configJson = baseConfigJson("RawData", "WRITE");
            String rawText    = "just plain text, not json at all";

             
            String result = service.processData(configJson, rawText);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("RawData");
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
        }

        @Test
        @DisplayName("raw non-JSON text that looks like markdown table — uses markdown formatter")
        void processData_rawTextInput_markdownTable_usesMarkdownFormatter() throws Exception {
             
            String configJson = baseConfigJson("Table", "WRITE");
            String rawMarkdown = "| A | B | C |\n|---|---|---|\n| 1 | 2 | 3 |";

             
            String result = service.processData(configJson, rawMarkdown);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(2);
        }
    }


    @Nested
    @DisplayName("Format 4: Structured JSON array (jsonData field — GPT outputFormat=json)")
    class JsonDataArrayFormat {

        private final String FULL_GPT_RESPONSE =
                "{\"jsonData\":["
                + "{\"rank\":\"1\",\"name\":\"Fatima Alaoui\",\"email\":\"fatima@test.com\","
                + "\"experience\":\"10 years\",\"skills\":\"Java, Spring Boot\",\"score\":\"9/10\","
                + "\"summary\":\"Excellent candidate\"},"
                + "{\"rank\":\"2\",\"name\":\"Nour El Houda\",\"email\":\"nour@test.com\","
                + "\"experience\":\"6 years\",\"skills\":\"Angular, Spring Boot\",\"score\":\"8.5/10\","
                + "\"summary\":\"Seasoned developer\"}"
                + "],\"analysis\":\"raw text\",\"model\":\"llama3.2:3b\",\"tokensUsed\":150}";

        @Test
        @DisplayName("happy path — jsonData array produces header + data rows")
        void processData_jsonDataArray_happyPath() throws Exception {
            String configJson = baseConfigJson("Rankings", "WRITE");

            String result = service.processData(configJson, FULL_GPT_RESPONSE);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Rankings");
            assertThat(response.getRowsProcessed()).isEqualTo(3);
            assertThat(response.getColumnsProcessed()).isEqualTo(7);
            assertThat(response.getFileContent()).isNotBlank();
            assertThat(response.getFileSize()).isPositive();
            assertThat(response.getOutputFileUrl()).isNotBlank();
        }

        @Test
        @DisplayName("single-item jsonData array — header + 1 row = 2 rows total")
        void processData_jsonDataArray_singleItem() throws Exception {
            String configJson = baseConfigJson("Results", "WRITE");
            String singleItem =
                    "{\"jsonData\":[{\"rank\":\"1\",\"name\":\"Alice\",\"score\":\"9/10\"}],"
                    + "\"analysis\":\"Alice\",\"model\":\"llama3.2:3b\",\"tokensUsed\":50}";

            String result = service.processData(configJson, singleItem);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(2);
            assertThat(response.getColumnsProcessed()).isEqualTo(3);
        }

        @Test
        @DisplayName("jsonData takes priority over analysis field when both present")
        void processData_jsonDataTakesPriorityOverAnalysis() throws Exception {
            String configJson = baseConfigJson("Priority", "WRITE");
            String bothPresent =
                    "{\"jsonData\":[{\"name\":\"Alice\",\"score\":\"9\"}],"
                    + "\"analysis\":\"plain text that should NOT drive row count\","
                    + "\"model\":\"test\",\"tokensUsed\":10}";

            String result = service.processData(configJson, bothPresent);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(2);
            assertThat(response.getColumnsProcessed()).isEqualTo(2);
        }

        @Test
        @DisplayName("jsonData output file exists on disk")
        void processData_jsonDataArray_fileExistsOnDisk() throws Exception {
            String configJson = baseConfigJson("Data", "WRITE");

            String result = service.processData(configJson, FULL_GPT_RESPONSE);

            ExcelResponseDTO response = parseResponse(result);
            File f = new File(response.getOutputFileUrl());
            assertThat(f).exists();
            assertThat(f.getName()).endsWith(".xlsx");
        }

        @Test
        @DisplayName("empty jsonData array falls through to analysis path")
        void processData_emptyJsonDataArray_fallsToAnalysisPath() throws Exception {
            String configJson = baseConfigJson("Empty", "WRITE");
            String emptyArray =
                    "{\"jsonData\":[],"
                    + "\"analysis\":\"No candidates matched.\","
                    + "\"model\":\"llama3.2:3b\",\"tokensUsed\":20}";

            String result = service.processData(configJson, emptyArray);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isGreaterThan(0);
            assertThat(response.getColumnsProcessed()).isEqualTo(1);
        }
    }



    @Nested
    @DisplayName("Format 5: JSON table (input is a JSON array without 'analysis' field)")
    class JsonTableFormat {

        @Test
        @DisplayName("happy path — JSON array produces valid response with columnsProcessed = array size")
        void processData_jsonArray_happyPath() throws Exception {
             
            String configJson = baseConfigJson("Report", "WRITE");
            String inputData  = "[\"Alice\",\"Bob\",\"Carol\"]";

             
            String result = service.processData(configJson, inputData);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Report");
            assertThat(response.getColumnsProcessed()).isEqualTo(3);
            assertThat(response.getFileContent()).isNotBlank();
            assertThat(response.getFileSize()).isPositive();
        }

        @Test
        @DisplayName("JSON array with multiple elements — columnsProcessed equals element count")
        void processData_jsonArray_elementCountCorrect() throws Exception {
             
            String configJson = baseConfigJson("Data", "WRITE");
            String inputData  = "[1, 2, 3, 4, 5]";

             
            String result = service.processData(configJson, inputData);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getColumnsProcessed()).isEqualTo(5);
        }

        @Test
        @DisplayName("analysis field with plain text — falls back to adaptive text formatter (columnsProcessed=1)")
        void processData_jsonWithAnalysisField_plainText_usesTextFormatter() throws Exception {
            String configJson = baseConfigJson("Data", "WRITE");
            String inputData  = "{\"analysis\":\"Short plain text result\"}";

             
            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getColumnsProcessed()).isEqualTo(1);
        }

        @Test
        @DisplayName("analysis field containing JSON array — routes to professional table (new always-JSON path)")
        void processData_analysisFieldContainsJsonArray_professionalTable() throws Exception {
            String configJson = baseConfigJson("Rankings", "WRITE");
            String inputData  = "{\"analysis\":\"[{\\\"rank\\\":\\\"1\\\",\\\"name\\\":\\\"Alice\\\","
                    + "\\\"email\\\":\\\"alice@test.com\\\",\\\"score\\\":\\\"9/10\\\"},"
                    + "{\\\"rank\\\":\\\"2\\\",\\\"name\\\":\\\"Bob\\\","
                    + "\\\"email\\\":\\\"bob@test.com\\\",\\\"score\\\":\\\"7/10\\\"}]\","
                    + "\"model\":\"llama3.2:3b\",\"tokensUsed\":80}";

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Rankings");
            assertThat(response.getRowsProcessed()).isEqualTo(3);
            assertThat(response.getColumnsProcessed()).isEqualTo(4);
            assertThat(response.getFileContent()).isNotBlank();
            assertThat(response.getFileSize()).isPositive();
        }

        @Test
        @DisplayName("analysis field with markdown-fenced JSON array — strips fences and parses correctly")
        void processData_analysisFieldWithFencedJson_parsedCorrectly() throws Exception {
            String configJson = baseConfigJson("Data", "WRITE");

            String fencedJson = "```json\\n[{\\\"name\\\":\\\"Alice\\\",\\\"score\\\":\\\"9/10\\\"}]\\n```";
            String inputData  = "{\"analysis\":\"" + fencedJson + "\",\"model\":\"llama3.2:3b\",\"tokensUsed\":30}";

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getRowsProcessed()).isEqualTo(2);
            assertThat(response.getColumnsProcessed()).isEqualTo(2);
        }

        @Test
        @DisplayName("plain JSON object without 'analysis' — throws RuntimeException (known production limitation)")
        void processData_jsonObject_throwsRuntimeException() {
            String configJson = baseConfigJson("Report", "WRITE");
            String inputData  = "{\"name\":\"Alice\",\"score\":\"9\"}";


            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.processData(configJson, inputData));
            assertThat(ex.getMessage()).contains("Excel processing failed");
        }
    }


    @Nested
    @DisplayName("Config variations")
    class ConfigVariations {

        @Test
        @DisplayName("null sheetName in config defaults to 'Data'")
        void processData_nullSheetName_defaultsToData() throws Exception {
            String configJson = "{\"operation\":\"WRITE\"}";
            String inputData  = analysisInputJson("some analysis");

             
            String result = service.processData(configJson, inputData);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("Data");
        }

        @Test
        @DisplayName("null operation in config defaults to 'WRITE'")
        void processData_nullOperation_defaultsToWrite() throws Exception {
             
            String configJson = "{\"sheetName\":\"Sheet1\"}";
            String inputData  = analysisInputJson("result text");

             
            String result = service.processData(configJson, inputData);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getOperation()).isEqualTo("WRITE");
        }

        @Test
        @DisplayName("custom sheetName is reflected in response and file")
        void processData_customSheetName() throws Exception {
             
            String configJson = baseConfigJson("MyCustomSheet", "WRITE");
            String inputData  = analysisInputJson("custom sheet data");

             
            String result = service.processData(configJson, inputData);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getSheetName()).isEqualTo("MyCustomSheet");
        }

        @Test
        @DisplayName("exception — invalid configJson throws RuntimeException")
        void processData_invalidConfigJson_throws() {
             
            String brokenConfig = "{not valid json}";
            String inputData    = "{\"key\":\"val\"}";

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.processData(brokenConfig, inputData));
            assertThat(ex.getMessage()).contains("Excel processing failed");
        }
    }


    @Nested
    @DisplayName("readData() — READ operation")
    class ReadData {

        private Path writeXlsx(String folder, String fileName, String[] headers, String[]... dataRows)
                throws Exception {
            Path dir = folder != null ? tempDir.resolve(folder) : tempDir;
            Files.createDirectories(dir);
            Path file = dir.resolve(fileName);
            try (XSSFWorkbook wb = new XSSFWorkbook();
                 FileOutputStream fos = new FileOutputStream(file.toFile())) {
                Sheet sheet = wb.createSheet("Data");
                Row header = sheet.createRow(0);
                for (int i = 0; i < headers.length; i++) {
                    header.createCell(i).setCellValue(headers[i]);
                }
                for (int r = 0; r < dataRows.length; r++) {
                    Row row = sheet.createRow(r + 1);
                    for (int c = 0; c < dataRows[r].length; c++) {
                        row.createCell(c).setCellValue(dataRows[r][c]);
                    }
                }
                wb.write(fos);
            }
            return file;
        }

        private String configJson(String folderId, String fileName) {
            if (folderId != null) {
                return String.format(
                    "{\"operation\":\"READ\",\"folderId\":\"%s\",\"fileName\":\"%s\"}",
                    folderId, fileName);
            }
            return String.format("{\"operation\":\"READ\",\"fileName\":\"%s\"}", fileName);
        }

        @Test
        @DisplayName("happy path XLSX — returns JSON array with headers as keys")
        void readData_xlsx_happyPath() throws Exception {
            writeXlsx("candidates", "data.xlsx",
                new String[]{"Name", "Email", "Department"},
                new String[]{"Fatima", "fatima@test.com", "Engineering"},
                new String[]{"Ahmed", "ahmed@test.com", "HR"});

            String result = service.readData(configJson("candidates", "data.xlsx"));

            JsonNode arr = objectMapper.readTree(result);
            assertThat(arr.isArray()).isTrue();
            assertThat(arr.size()).isEqualTo(2);
            assertThat(arr.get(0).get("Name").asText()).isEqualTo("Fatima");
            assertThat(arr.get(0).get("Email").asText()).isEqualTo("fatima@test.com");
            assertThat(arr.get(1).get("Department").asText()).isEqualTo("HR");
        }

        @Test
        @DisplayName("happy path CSV — returns JSON array")
        void readData_csv_happyPath() throws Exception {
            Path dir = tempDir.resolve("employees");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("staff.csv"),
                "Name,Email,Salary\nAlice,alice@test.com,50000\nBob,bob@test.com,45000\n");

            String result = service.readData(configJson("employees", "staff.csv"));

            JsonNode arr = objectMapper.readTree(result);
            assertThat(arr.isArray()).isTrue();
            assertThat(arr.size()).isEqualTo(2);
            assertThat(arr.get(0).get("Name").asText()).isEqualTo("Alice");
            assertThat(arr.get(1).get("Salary").asText()).isEqualTo("45000");
        }

        @Test
        @DisplayName("XLSX headers only (no data rows) — returns empty array")
        void readData_xlsx_headersOnly_returnsEmptyArray() throws Exception {
            writeXlsx("data", "empty.xlsx", new String[]{"Name", "Email"});

            String result = service.readData(configJson("data", "empty.xlsx"));

            JsonNode arr = objectMapper.readTree(result);
            assertThat(arr.isArray()).isTrue();
            assertThat(arr.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("CSV with only header line — returns empty array")
        void readData_csv_headerOnly_returnsEmptyArray() throws Exception {
            Path dir = tempDir.resolve("data");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("header-only.csv"), "Name,Email\n");

            String result = service.readData(configJson("data", "header-only.csv"));

            JsonNode arr = objectMapper.readTree(result);
            assertThat(arr.isArray()).isTrue();
            assertThat(arr.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("file not found — throws RuntimeException")
        void readData_fileNotFound_throws() {
            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.readData(configJson("missing-folder", "ghost.xlsx")));
            assertThat(ex.getMessage()).contains("Excel read failed");
        }

        @Test
        @DisplayName("missing fileName — throws RuntimeException")
        void readData_missingFileName_throws() {
            RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.readData("{\"operation\":\"READ\",\"folderId\":\"candidates\"}"));
            assertThat(ex.getMessage()).contains("Excel read failed");
        }

        @Test
        @DisplayName("no folderId — resolves file directly in storage root")
        void readData_noFolderId_resolvesInRoot() throws Exception {
            writeXlsx(null, "root.xlsx",
                new String[]{"Col"},
                new String[]{"Val"});

            String result = service.readData("{\"operation\":\"READ\",\"fileName\":\"root.xlsx\"}");

            JsonNode arr = objectMapper.readTree(result);
            assertThat(arr.size()).isEqualTo(1);
            assertThat(arr.get(0).get("Col").asText()).isEqualTo("Val");
        }
    }


    @Nested
    @DisplayName("Response structure — invariants for all formats")
    class ResponseInvariants {

        @Test
        @DisplayName("response always contains non-null outputFileUrl, webFileUrl, fileContent")
        void processData_responseHasRequiredFields() throws Exception {
            String configJson = baseConfigJson("Sheet", "WRITE");
            String inputData  = analysisInputJson("Summary of results");

             
            String result = service.processData(configJson, inputData);

              
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
             
            String configJson = baseConfigJson("Sheet", "WRITE");
            String inputData  = analysisInputJson("Some analysis text here");

             
            String result = service.processData(configJson, inputData);

              
            ExcelResponseDTO response = parseResponse(result);
            assertThat(response.getWebFileUrl())
                    .matches("/api/v1/files/excel/workflow-\\d+-[a-f0-9]+\\.xlsx");
        }

        @Test
        @DisplayName("fileContent is valid Base64-encoded bytes")
        void processData_fileContentIsBase64() throws Exception {
            String configJson = baseConfigJson("Sheet", "WRITE");
            String inputData  = analysisInputJson("Some plain text result for encoding test");

            String result = service.processData(configJson, inputData);

            ExcelResponseDTO response = parseResponse(result);
            assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(response.getFileContent()),
                    "fileContent should be valid Base64");
            assertThat(java.util.Base64.getDecoder().decode(response.getFileContent())).isNotEmpty();
        }
    }
}
