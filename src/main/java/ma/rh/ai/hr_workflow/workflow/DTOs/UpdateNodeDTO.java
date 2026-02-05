package ma.rh.ai.hr_workflow.workflow.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor

public class UpdateNodeDTO {
    private String type;
    private Integer order;
    private String configJson;
}