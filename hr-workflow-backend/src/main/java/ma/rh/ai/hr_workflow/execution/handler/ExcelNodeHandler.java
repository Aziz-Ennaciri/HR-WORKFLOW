package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.excel.DTOs.ExcelConfigDTO;
import ma.rh.ai.hr_workflow.integration.excel.service.ExcelService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

@Component
@RequiredArgsConstructor
public class ExcelNodeHandler implements NodeHandler {

    private final ExcelService excelService;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.EXCEL;
    }

    @Override
    public String execute(Node node, NodeInstance nodeInstance) throws Exception {
        String enrichedConfig = enrichConfig(node.getConfigJson());
        ExcelConfigDTO config = objectMapper.readValue(enrichedConfig, ExcelConfigDTO.class);
        if ("READ".equals(config.getOperation())) {
            return excelService.readData(enrichedConfig);
        }
        return excelService.processData(enrichedConfig, nodeInstance.getInputData());
    }

    // Auto-fill WRITE defaults so the frontend only needs to send {fileName: "..."}
    String enrichConfig(String configJson) throws Exception {
        ExcelConfigDTO config = objectMapper.readValue(configJson, ExcelConfigDTO.class);

        if (config.getOperation() == null) {
            config.setOperation("WRITE");
        }

        if (!"READ".equals(config.getOperation())) {
            if (config.getFileFormat() == null) {
                config.setFileFormat("XLSX");
            }
            if (config.getSheetName() == null) {
                config.setSheetName(config.getFileName() != null ? config.getFileName() : "Report");
            }
        }

        return objectMapper.writeValueAsString(config);
    }
}
