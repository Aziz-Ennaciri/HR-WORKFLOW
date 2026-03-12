package ma.rh.ai.hr_workflow.integration.excel.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExcelConfigDTO {
    private String sheetName;
    private String operation;
    private Integer startRow;
    private Integer startColumn;
    private String outputPath;
    private String fileFormat; // CSV, XLS, XLSX
    private String fileName;   // Name of the file to create/read
    private String uploadedFile; // Base64-encoded file content for READ operations
}
