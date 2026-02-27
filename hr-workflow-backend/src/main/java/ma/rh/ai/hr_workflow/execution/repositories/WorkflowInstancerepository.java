package ma.rh.ai.hr_workflow.execution.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;

@Repository
public interface WorkflowInstancerepository extends JpaRepository<WorkflowInstance,Long>{

    List<WorkflowInstance> findByWorkflowIdOrderByCreatedAtDesc(Long workflowId);
    
}
