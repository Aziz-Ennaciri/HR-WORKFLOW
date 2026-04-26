package ma.rh.ai.hr_workflow.execution.DTOs;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class NodeInstanceResponseDTO {

    private Long id;
    private Long workflowInstanceId;
    private Long nodeId;
    private String nodeType;
    private Integer executionOrder;
    private String status;
    private String actor;
    private String comment;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMs;
    private String inputData;
    private String outputData;
    private String errorMessage;
    private Integer retryCount;
    private LocalDateTime createdAt;

    private Long assignedToId;
    private String assignedToEmail;
    private String assignedToName;

}