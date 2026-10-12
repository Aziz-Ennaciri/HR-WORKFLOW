package ma.rh.ai.hr_workflow.execution.repositories;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;

@Repository
public interface WorkflowInstancerepository extends JpaRepository<WorkflowInstance,Long>{

    List<WorkflowInstance> findByWorkflowIdOrderByCreatedAtDesc(Long workflowId);

    List<WorkflowInstance> findByWorkflowIdAndStatusIn(Long workflowId, List<WorkflowInstanceStatus> statuses);

    List<WorkflowInstance> findByWorkflow_CreatedBy_Email(String email);

    @Query("select wi.id from WorkflowInstance wi where wi.status = :status")
    List<Long> findIdsByStatus(@Param("status") WorkflowInstanceStatus status);

    /** Executions that were created but never picked up by a worker (e.g. queue full, crash before start). */
    @Query("select wi.id from WorkflowInstance wi where wi.status = ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus.PENDING and wi.createdAt < :cutoff")
    List<Long> findStalePendingIds(@Param("cutoff") LocalDateTime cutoff);

    /** Paused executions whose approval was already decided, i.e. the resume was lost. */
    @Query("""
            select wi.id from WorkflowInstance wi where wi.status = ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus.PAUSED and not exists (
                select 1 from NodeInstance ni where ni.workflowInstance = wi and ni.status = ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus.WAITING_APPROVAL)
            """)
    List<Long> findPausedIdsWithoutPendingApproval();
}
