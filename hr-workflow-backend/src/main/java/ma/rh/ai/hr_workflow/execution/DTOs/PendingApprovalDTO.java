package ma.rh.ai.hr_workflow.execution.DTOs;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendingApprovalDTO {

    private Long nodeInstanceId;
    private String status;
    private String inputData;
    private LocalDateTime createdAt;
    private String instructions;

    private Long workflowInstanceId;
    private String workflowInstanceStatus;

    private Long workflowId;
    private String workflowName;

    private Long triggeredById;
    private String triggeredByEmail;
    private String triggeredByName;
}