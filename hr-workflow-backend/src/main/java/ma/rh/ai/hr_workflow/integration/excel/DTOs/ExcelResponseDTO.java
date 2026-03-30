package ma.rh.ai.hr_workflow.integration.excel.DTOs;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ExcelResponseDTO {
    private Integer rowsProcessed;
    private Integer columnsProcessed;
    private String outputFileUrl;
    private Long fileSize;
    private String sheetName;
    private String operation;
    private LocalDateTime createdAt;
    private List<Object> data;
    private String fileContent;
}
