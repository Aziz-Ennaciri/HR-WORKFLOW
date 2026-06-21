package ma.rh.ai.hr_workflow.integration.excel.service;

public interface ExcelService {
    String processData(String configJson, String inputData) throws Exception;
    String readData(String configJson) throws Exception;
}
