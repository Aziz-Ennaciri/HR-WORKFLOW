package ma.rh.ai.hr_workflow.execution.DTOs;

import java.util.List;
import java.util.Map;

/** Execution counts for the dashboard, computed in the database rather than from the full list. */
public record ExecutionStatsDTO(
        long total,
        Map<String, Long> byStatus,
        List<WorkflowExecutionCount> byWorkflow) {

    public record WorkflowExecutionCount(Long workflowId, String workflowName, long total) {
    }
}
