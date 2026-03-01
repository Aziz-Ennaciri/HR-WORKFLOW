package ma.rh.ai.hr_workflow.execution.mappers;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-02-23T22:36:16+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class WorkflowInstanceMapperImpl implements WorkflowInstanceMapper {

    @Override
    public WorkflowInstanceResponseDTO toResponseDTO(WorkflowInstance workflowInstance) {
        if ( workflowInstance == null ) {
            return null;
        }

        WorkflowInstanceResponseDTO workflowInstanceResponseDTO = new WorkflowInstanceResponseDTO();

        workflowInstanceResponseDTO.setWorkflowId( workflowInstanceWorkflowId( workflowInstance ) );
        workflowInstanceResponseDTO.setWorkflowName( workflowInstanceWorkflowName( workflowInstance ) );
        workflowInstanceResponseDTO.setTriggeredById( workflowInstanceTriggeredById( workflowInstance ) );
        workflowInstanceResponseDTO.setTriggeredByEmail( workflowInstanceTriggeredByEmail( workflowInstance ) );
        workflowInstanceResponseDTO.setCurrentNodeId( workflowInstanceCurrentNodeId( workflowInstance ) );
        workflowInstanceResponseDTO.setId( workflowInstance.getId() );
        workflowInstanceResponseDTO.setStartedAt( workflowInstance.getStartedAt() );
        workflowInstanceResponseDTO.setFinishedAt( workflowInstance.getFinishedAt() );
        workflowInstanceResponseDTO.setDurationMs( workflowInstance.getDurationMs() );
        workflowInstanceResponseDTO.setInputData( workflowInstance.getInputData() );
        workflowInstanceResponseDTO.setOutputData( workflowInstance.getOutputData() );
        workflowInstanceResponseDTO.setErrorMessage( workflowInstance.getErrorMessage() );
        workflowInstanceResponseDTO.setCreatedAt( workflowInstance.getCreatedAt() );

        workflowInstanceResponseDTO.setCurrentNodeType( workflowInstance.getCurrentNode() != null ? workflowInstance.getCurrentNode().getType().name() : null );
        workflowInstanceResponseDTO.setStatus( workflowInstance.getStatus().name() );

        return workflowInstanceResponseDTO;
    }

    @Override
    public List<WorkflowInstanceResponseDTO> toResponseDTO(List<WorkflowInstance> workflowInstances) {
        if ( workflowInstances == null ) {
            return null;
        }

        List<WorkflowInstanceResponseDTO> list = new ArrayList<WorkflowInstanceResponseDTO>( workflowInstances.size() );
        for ( WorkflowInstance workflowInstance : workflowInstances ) {
            list.add( toResponseDTO( workflowInstance ) );
        }

        return list;
    }

    private Long workflowInstanceWorkflowId(WorkflowInstance workflowInstance) {
        if ( workflowInstance == null ) {
            return null;
        }
        Workflow workflow = workflowInstance.getWorkflow();
        if ( workflow == null ) {
            return null;
        }
        Long id = workflow.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private String workflowInstanceWorkflowName(WorkflowInstance workflowInstance) {
        if ( workflowInstance == null ) {
            return null;
        }
        Workflow workflow = workflowInstance.getWorkflow();
        if ( workflow == null ) {
            return null;
        }
        String name = workflow.getName();
        if ( name == null ) {
            return null;
        }
        return name;
    }

    private Long workflowInstanceTriggeredById(WorkflowInstance workflowInstance) {
        if ( workflowInstance == null ) {
            return null;
        }
        User triggeredBy = workflowInstance.getTriggeredBy();
        if ( triggeredBy == null ) {
            return null;
        }
        Long id = triggeredBy.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private String workflowInstanceTriggeredByEmail(WorkflowInstance workflowInstance) {
        if ( workflowInstance == null ) {
            return null;
        }
        User triggeredBy = workflowInstance.getTriggeredBy();
        if ( triggeredBy == null ) {
            return null;
        }
        String email = triggeredBy.getEmail();
        if ( email == null ) {
            return null;
        }
        return email;
    }

    private Long workflowInstanceCurrentNodeId(WorkflowInstance workflowInstance) {
        if ( workflowInstance == null ) {
            return null;
        }
        Node currentNode = workflowInstance.getCurrentNode();
        if ( currentNode == null ) {
            return null;
        }
        Long id = currentNode.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
