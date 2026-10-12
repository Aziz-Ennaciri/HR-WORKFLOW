package ma.rh.ai.hr_workflow.execution.service;

import java.util.List;

import org.springframework.data.domain.Pageable;

import ma.rh.ai.hr_workflow.common.PageResponse;
import ma.rh.ai.hr_workflow.execution.DTOs.ExecutionStatsDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.NodeInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.PendingApprovalDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceDetailDTO;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;

/** Read side of executions; every method maps to DTOs inside its own read-only transaction. */
public interface IExecutionQueryService {

    WorkflowInstanceResponseDTO getExecution(Long id);

    WorkflowInstanceDetailDTO getExecutionDetail(Long id);

    List<WorkflowInstanceResponseDTO> getExecutionsForWorkflow(Long workflowId);

    /** Executions visible to the current user (all for admins), newest first. */
    PageResponse<WorkflowInstanceResponseDTO> searchExecutions(WorkflowInstanceStatus status, Long workflowId, Pageable pageable);

    ExecutionStatsDTO getStats();

    NodeInstanceResponseDTO getNodeInstance(Long id);

    List<NodeInstanceResponseDTO> getNodeInstances(Long executionId);

    List<PendingApprovalDTO> getMyApprovals();

    long countMyApprovals();

    void deleteExecution(Long id);
}
