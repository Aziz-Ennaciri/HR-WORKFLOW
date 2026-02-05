package ma.rh.ai.hr_workflow.workflow.DTOs;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowResponseDTO {
    private Long id;
    private String name;
    private String description;
    private String workflowKey;
    private Integer version;
    private String status;
    private LocalDateTime createdAt;
}
