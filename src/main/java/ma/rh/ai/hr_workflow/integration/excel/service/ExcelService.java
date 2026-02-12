package ma.rh.ai.hr_workflow.integration.excel.service;

public interface ExcelService {
    String processExcel(String configJson, String inputData) throws Exception;
}
