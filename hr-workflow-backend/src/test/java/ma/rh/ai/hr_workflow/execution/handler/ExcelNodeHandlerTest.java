package ma.rh.ai.hr_workflow.execution.handler;

import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.excel.service.ExcelService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelNodeHandler")
class ExcelNodeHandlerTest {

    @Mock
    private ExcelService excelService;

    private ExcelNodeHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ExcelNodeHandler(excelService);
    }

    @Test
    @DisplayName("getType() returns NodeType.EXCEL")
    void getType_returnsExcel() {
        assertThat(handler.getType()).isEqualTo(NodeType.EXCEL);
    }

    @Test
    @DisplayName("execute() delegates to excelService.processData with configJson and inputData")
    void execute_delegatesCorrectArguments() throws Exception {
        // Arrange
        Node node = new Node();
        node.setConfigJson("{\"format\":\"xlsx\"}");

        NodeInstance ni = new NodeInstance();
        ni.setInputData("{\"analysis\":\"Alice is best\"}");

        when(excelService.processData(any(), any())).thenReturn("/output/report.xlsx");

        // Act
        String result = handler.execute(node, ni);

        // Assert
        assertThat(result).isEqualTo("/output/report.xlsx");
        verify(excelService).processData(
                "{\"format\":\"xlsx\"}",
                "{\"analysis\":\"Alice is best\"}");
    }
}
