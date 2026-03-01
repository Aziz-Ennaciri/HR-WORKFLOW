package ma.rh.ai.hr_workflow.workflow.mappers;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.UpdateWorkflowDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowResponseDTO;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import ma.rh.ai.hr_workflow.workflow.model.WorkflowStatus;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-02-23T22:36:16+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class WorkflowMapperImpl implements WorkflowMapper {

    @Override
    public Workflow toEntity(CreateWorkflowDTO dto, User creator) {
        if ( dto == null && creator == null ) {
            return null;
        }

        Workflow workflow = new Workflow();

        if ( dto != null ) {
            workflow.setName( dto.getName() );
            workflow.setDescription( dto.getDescription() );
            workflow.setWorkflowKey( dto.getWorkflowKey() );
        }
        workflow.setCreatedBy( creator );
        workflow.setStatus( WorkflowStatus.DRAFT );
        workflow.setVersion( 1 );
        workflow.setCreatedAt( java.time.LocalDateTime.now() );

        return workflow;
    }

    @Override
    public void updateEntity(UpdateWorkflowDTO dto, Workflow workflow) {
        if ( dto == null ) {
            return;
        }

        if ( dto.getName() != null ) {
            workflow.setName( dto.getName() );
        }
        if ( dto.getDescription() != null ) {
            workflow.setDescription( dto.getDescription() );
        }
    }

    @Override
    public WorkflowResponseDTO toResponseDTO(Workflow workflow) {
        if ( workflow == null ) {
            return null;
        }

        WorkflowResponseDTO workflowResponseDTO = new WorkflowResponseDTO();

        workflowResponseDTO.setId( workflow.getId() );
        workflowResponseDTO.setName( workflow.getName() );
        workflowResponseDTO.setDescription( workflow.getDescription() );
        workflowResponseDTO.setWorkflowKey( workflow.getWorkflowKey() );
        workflowResponseDTO.setVersion( workflow.getVersion() );
        workflowResponseDTO.setCreatedAt( workflow.getCreatedAt() );

        workflowResponseDTO.setStatus( workflow.getStatus().name() );

        return workflowResponseDTO;
    }

    @Override
    public List<WorkflowResponseDTO> toResponseDTO(List<Workflow> workflows) {
        if ( workflows == null ) {
            return null;
        }

        List<WorkflowResponseDTO> list = new ArrayList<WorkflowResponseDTO>( workflows.size() );
        for ( Workflow workflow : workflows ) {
            list.add( toResponseDTO( workflow ) );
        }

        return list;
    }
}
