package ma.rh.ai.hr_workflow.workflow.repositories;

import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NodeRepository extends JpaRepository<Node,Long> {

    List<Node> findByWorkflowIdOrderByOrderIndexAsc(Long workflowId);

    List<Node> findByWorkflowIdAndType(Long workflowId, NodeType nodeType);

    @Modifying
    @Query("DELETE FROM Node n WHERE n.workflow.id = :workflowId")
    void deleteByWorkflowId(@Param("workflowId") Long workflowId);
}
