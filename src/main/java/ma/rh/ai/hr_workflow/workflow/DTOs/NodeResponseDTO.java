package ma.rh.ai.hr_workflow.workflow.DTOs;

import lombok.*;

@Data
@Setter
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class NodeResponseDTO {
    private Long id;
    private String type;
    private Integer order;
    private String configJson;
}
