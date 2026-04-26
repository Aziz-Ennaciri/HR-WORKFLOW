package ma.rh.ai.hr_workflow.execution.repositories;

import java.util.List;
import java.util.Optional;

import ma.rh.ai.hr_workflow.execution.model.NodeInstanceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import ma.rh.ai.hr_workflow.execution.model.NodeInstance;

@Repository
public interface NodeInstanceRepository extends JpaRepository<NodeInstance,Long>{

    Optional<NodeInstance> findByWorkflowInstanceIdAndNodeId(Long workflowInstanceId, Long nodeId);

    List<NodeInstance> findByWorkflowInstanceIdOrderByExecutionOrderAsc(Long instanceId);

    List<NodeInstance> findByAssignedToIdAndStatusOrderByCreatedAtDesc(Long userId, NodeInstanceStatus status);

    long countByAssignedToIdAndStatus(Long userId, NodeInstanceStatus status);
}
