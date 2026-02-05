package ma.rh.ai.hr_workflow.workflow.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeResponseDTO {
    private Long id;
    private String type;
    private Integer order;
    private String configJson;
}
