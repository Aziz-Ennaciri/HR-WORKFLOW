package ma.rh.ai.hr_workflow.execution.repositories;

import java.util.List;
import java.util.Optional;

import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import ma.rh.ai.hr_workflow.execution.model.NodeInstance;

@Repository
public interface NodeInstanceRepository extends JpaRepository<NodeInstance,Long>{

    Optional<NodeInstance> findByWorkflowInstanceIdAndNodeId(Long workflowInstanceId, Long nodeId);

    List<NodeInstance> findByWorkflowInstanceIdOrderByExecutionOrderAsc(Long instanceId);

    List<NodeInstance> findByWorkflowInstanceIdAndStatus(Long instanceId, NodeInstanceStatus status);

    boolean existsByWorkflowInstanceIdAndStatus(Long instanceId, NodeInstanceStatus status);

    List<NodeInstance> findByAssignedToIdAndStatusOrderByCreatedAtDesc(Long userId, NodeInstanceStatus status);

    long countByAssignedToIdAndStatus(Long userId, NodeInstanceStatus status);

    @EntityGraph(attributePaths = {"workflowInstance", "workflowInstance.workflow", "workflowInstance.triggeredBy"})
    List<NodeInstance> findByAssignedToEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(String email, NodeInstanceStatus status);

    @EntityGraph(attributePaths = {"node", "assignedTo"})
    List<NodeInstance> findWithDetailsByWorkflowInstanceIdOrderByExecutionOrderAsc(Long instanceId);

    boolean existsByWorkflowInstanceIdAndAssignedToEmailIgnoreCase(Long instanceId, String email);

    long countByAssignedToEmailIgnoreCaseAndStatus(String email, NodeInstanceStatus status);
}
