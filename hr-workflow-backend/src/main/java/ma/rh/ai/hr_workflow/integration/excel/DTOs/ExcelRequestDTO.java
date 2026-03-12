package ma.rh.ai.hr_workflow.integration.excel.DTOs;

import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExcelRequestDTO {
    private String fileUrl;
    private String sheetName;
    private List<Map<String, Object>> data;
    private Map<String,String> variables;
}
