package ma.rh.ai.hr_workflow.integration.excel.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExcelConfigDTO {
    private String operation;
    private String sheetName;
    private Integer headerRow;
    private boolean skipEmptyRows;
}
