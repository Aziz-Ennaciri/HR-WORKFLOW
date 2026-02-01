package ma.rh.ai.hr_workflow.workflow.DTOs;

import java.time.LocalDateTime;

public class WorkflowResponseDTO {
    private Long id;
    private String name;
    private String description;
    private Integer version;
    private String status;
    private LocalDateTime createdAt;
}
