package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.excel.service.ExcelService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

@Component
@RequiredArgsConstructor
public class ExcelNodeHandler implements NodeHandler {

    private final ExcelService excelService;

    @Override
    public NodeType getType() {
        return NodeType.EXCEL;
    }

    @Override
    public String execute(Node node, NodeInstance nodeInstance) throws Exception {
        String configJson = node.getConfigJson();
        String inputData = nodeInstance.getInputData();
        
        String result = excelService.processData(configJson, inputData);
        
        return result;
    }
}