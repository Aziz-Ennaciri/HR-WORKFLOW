package ma.rh.ai.hr_workflow.execution.repositories;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstanceStatus;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long>,
        JpaSpecificationExecutor<WorkflowInstance> {

    /** Fetches what the list DTO shows in the same query (no N+1 per row). */
    @Override
    @EntityGraph(attributePaths = {"workflow", "triggeredBy", "currentNode"})
    Page<WorkflowInstance> findAll(Specification<WorkflowInstance> spec, Pageable pageable);

    @Query("select wi.status as status, count(wi) as total from WorkflowInstance wi group by wi.status")
    List<StatusCount> countByStatus();

    @Query("""
            select wi.status as status, count(wi) as total from WorkflowInstance wi
            where lower(wi.workflow.createdBy.email) = lower(:email) group by wi.status""")
    List<StatusCount> countByStatusForOwner(@Param("email") String email);

    @Query("""
            select w.id as workflowId, w.name as workflowName, count(wi) as total
            from WorkflowInstance wi join wi.workflow w group by w.id, w.name order by count(wi) desc""")
    List<WorkflowCount> countByWorkflow();

    @Query("""
            select w.id as workflowId, w.name as workflowName, count(wi) as total
            from WorkflowInstance wi join wi.workflow w
            where lower(w.createdBy.email) = lower(:email)
            group by w.id, w.name order by count(wi) desc""")
    List<WorkflowCount> countByWorkflowForOwner(@Param("email") String email);

    interface StatusCount {
        WorkflowInstanceStatus getStatus();
        long getTotal();
    }

    interface WorkflowCount {
        Long getWorkflowId();
        String getWorkflowName();
        long getTotal();
    }


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
