package ma.rh.ai.hr_workflow.execution.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelConfigDTO;
import ma.rh.ai.hr_workflow.integration.excel.service.ExcelService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelNodeHandler")
class ExcelNodeHandlerTest {

    @Mock
    private ExcelService excelService;

    private ObjectMapper objectMapper;
    private ExcelNodeHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        handler = new ExcelNodeHandler(excelService, objectMapper);
    }

    @Test
    @DisplayName("getType() returns NodeType.EXCEL")
    void getType_returnsExcel() {
        assertThat(handler.getType()).isEqualTo(NodeType.EXCEL);
    }

    @Test
    @DisplayName("execute() returns service result")
    void execute_returnsServiceResult() throws Exception {
        Node node = new Node();
        node.setConfigJson("{\"fileName\":\"Candidates\"}");
        NodeInstance ni = new NodeInstance();
        ni.setInputData("{\"analysis\":\"Alice is best\"}");
        when(excelService.processData(any(), any())).thenReturn("/output/report.xlsx");

        String result = handler.execute(node, ni);

        assertThat(result).isEqualTo("/output/report.xlsx");
    }

    @Test
    @DisplayName("execute() routes READ operation to readData, not processData")
    void execute_readOperation_routesToReadData() throws Exception {
        Node node = new Node();
        node.setConfigJson("{\"operation\":\"READ\",\"folderId\":\"candidates\",\"fileName\":\"candidates.xlsx\"}");
        NodeInstance ni = new NodeInstance();
        ni.setInputData("{}");
        when(excelService.readData(any())).thenReturn("[{\"Name\":\"Alice\"}]");

        String result = handler.execute(node, ni);

        assertThat(result).isEqualTo("[{\"Name\":\"Alice\"}]");
        verify(excelService).readData(any());
        verify(excelService, never()).processData(any(), any());
    }

    @Test
    @DisplayName("execute() routes WRITE operation to processData, not readData")
    void execute_writeOperation_routesToProcessData() throws Exception {
        Node node = new Node();
        node.setConfigJson("{\"operation\":\"WRITE\",\"fileName\":\"Report\"}");
        NodeInstance ni = new NodeInstance();
        ni.setInputData("{\"analysis\":\"data\"}");
        when(excelService.processData(any(), any())).thenReturn("/output/report.xlsx");

        String result = handler.execute(node, ni);

        assertThat(result).isEqualTo("/output/report.xlsx");
        verify(excelService).processData(any(), any());
        verify(excelService, never()).readData(any());
    }

    @Test
    @DisplayName("execute() passes inputData unchanged to service")
    void execute_passesInputDataUnchanged() throws Exception {
        Node node = new Node();
        node.setConfigJson("{\"fileName\":\"Report\"}");
        NodeInstance ni = new NodeInstance();
        ni.setInputData("{\"analysis\":\"Alice is best\"}");
        when(excelService.processData(any(), any())).thenReturn("ok");
        ArgumentCaptor<String> inputCaptor = ArgumentCaptor.forClass(String.class);

        handler.execute(node, ni);

        verify(excelService).processData(any(), inputCaptor.capture());
        assertThat(inputCaptor.getValue()).isEqualTo("{\"analysis\":\"Alice is best\"}");
    }

    // ── enrichConfig ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("enrichConfig() — auto-fill defaults")
    class EnrichConfig {

        @Test
        @DisplayName("minimal config {fileName} → operation=WRITE, fileFormat=XLSX, sheetName=fileName")
        void minimalConfig_fillsAllDefaults() throws Exception {
            String result = handler.enrichConfig("{\"fileName\":\"Candidate Rankings\"}");

            ExcelConfigDTO dto = objectMapper.readValue(result, ExcelConfigDTO.class);
            assertThat(dto.getOperation()).isEqualTo("WRITE");
            assertThat(dto.getFileFormat()).isEqualTo("XLSX");
            assertThat(dto.getSheetName()).isEqualTo("Candidate Rankings");
            assertThat(dto.getFileName()).isEqualTo("Candidate Rankings");
        }

        @Test
        @DisplayName("no fileName → sheetName defaults to 'Report'")
        void noFileName_sheetNameDefaultsToReport() throws Exception {
            String result = handler.enrichConfig("{}");

            ExcelConfigDTO dto = objectMapper.readValue(result, ExcelConfigDTO.class);
            assertThat(dto.getSheetName()).isEqualTo("Report");
        }

        @Test
        @DisplayName("explicit operation preserved — not overridden")
        void explicitOperation_preserved() throws Exception {
            String result = handler.enrichConfig("{\"operation\":\"READ\",\"fileName\":\"input\"}");

            ExcelConfigDTO dto = objectMapper.readValue(result, ExcelConfigDTO.class);
            assertThat(dto.getOperation()).isEqualTo("READ");
        }

        @Test
        @DisplayName("READ operation skips write-only defaults (fileFormat, sheetName stay null)")
        void readOperation_skipsWriteOnlyDefaults() throws Exception {
            String result = handler.enrichConfig(
                "{\"operation\":\"READ\",\"folderId\":\"candidates\",\"fileName\":\"candidates.xlsx\"}");

            ExcelConfigDTO dto = objectMapper.readValue(result, ExcelConfigDTO.class);
            assertThat(dto.getOperation()).isEqualTo("READ");
            assertThat(dto.getFileFormat()).isNull();
            assertThat(dto.getSheetName()).isNull();
        }

        @Test
        @DisplayName("explicit sheetName preserved — not overridden by fileName")
        void explicitSheetName_preserved() throws Exception {
            String result = handler.enrichConfig(
                "{\"fileName\":\"MyFile\",\"sheetName\":\"CustomSheet\"}");

            ExcelConfigDTO dto = objectMapper.readValue(result, ExcelConfigDTO.class);
            assertThat(dto.getSheetName()).isEqualTo("CustomSheet");
        }

        @Test
        @DisplayName("explicit fileFormat preserved — not overridden")
        void explicitFileFormat_preserved() throws Exception {
            String result = handler.enrichConfig("{\"fileFormat\":\"CSV\"}");

            ExcelConfigDTO dto = objectMapper.readValue(result, ExcelConfigDTO.class);
            assertThat(dto.getFileFormat()).isEqualTo("CSV");
        }
    }
}
