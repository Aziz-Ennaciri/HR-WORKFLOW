package ma.rh.ai.hr_workflow.execution.DTOs;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowInstanceResponseDTO {

    private Long id;
    private Long workflowId;
    private String workflowName;
    private Long triggeredById;
    private String triggeredByEmail;
    private Long currentNodeId;
    private String currentNodeType;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String inputData;
    private String outputData;
    private String errorMessage;
    private LocalDateTime createdAt;
}