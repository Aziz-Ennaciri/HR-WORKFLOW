package ma.rh.ai.hr_workflow.workflow.repositories;

import ma.rh.ai.hr_workflow.workflow.model.Workflow;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow,Long> {
//    List<Workflow> findByDeletedFalse();
    Page<Workflow> findByDeletedFalse(Pageable pageable);
    
    List<Workflow> findCreateById(Long creatorId);
    
    @Query("SELECT w FROM Workflow w LEFT JOIN FETCH w.nodes WHERE w.id = :id")
    Workflow findWorkflowWithNodes(@Param("id") Long id);
}
