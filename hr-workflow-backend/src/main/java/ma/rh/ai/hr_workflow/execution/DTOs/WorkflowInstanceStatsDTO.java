package ma.rh.ai.hr_workflow.execution.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WorkflowInstanceStatsDTO {

    private Long totalInstances;
    private Long completedInstances;
    private Long failedInstances;
    private Long runningInstances;
    private Long cancelledInstances;
    private Double successRate;
    private Long averageDurationMs;
}