package ma.rh.ai.hr_workflow.workflow.DTOs;

import lombok.Data;

@Data
public class NodeResponseDTO {
    private Long id;
    private String type;
    private Integer order;
    private String configJson;
}
