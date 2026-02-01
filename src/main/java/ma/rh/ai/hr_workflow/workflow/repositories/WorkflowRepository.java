package ma.rh.ai.hr_workflow.workflow.repositories;

import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow,Long> {
}
