package ma.rh.ai.hr_workflow.workflow.repositories;

import ma.rh.ai.hr_workflow.workflow.model.Workflow;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, Long> {
    @EntityGraph(attributePaths = "createdBy")
    List<Workflow> findByDeletedFalseOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = "createdBy")
    List<Workflow> findByCreatedByEmailIgnoreCaseAndDeletedFalseOrderByCreatedAtDesc(String email);
}