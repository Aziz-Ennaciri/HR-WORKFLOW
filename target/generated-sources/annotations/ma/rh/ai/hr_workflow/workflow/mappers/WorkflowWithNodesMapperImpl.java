package ma.rh.ai.hr_workflow.workflow.mappers;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeResponseDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-02-23T22:36:16+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class WorkflowWithNodesMapperImpl implements WorkflowWithNodesMapper {

    @Autowired
    private WorkflowMapper workflowMapper;
    @Autowired
    private NodeMapper nodeMapper;

    @Override
    public WorkflowWithNodesResponseDTO toDTO(Workflow workflow, List<Node> nodes) {
        if ( workflow == null && nodes == null ) {
            return null;
        }

        WorkflowWithNodesResponseDTO workflowWithNodesResponseDTO = new WorkflowWithNodesResponseDTO();

        workflowWithNodesResponseDTO.setWorkflow( workflowMapper.toResponseDTO( workflow ) );
        workflowWithNodesResponseDTO.setNodes( nodeListToNodeResponseDTOList( nodes ) );

        return workflowWithNodesResponseDTO;
    }

    protected List<NodeResponseDTO> nodeListToNodeResponseDTOList(List<Node> list) {
        if ( list == null ) {
            return null;
        }

        List<NodeResponseDTO> list1 = new ArrayList<NodeResponseDTO>( list.size() );
        for ( Node node : list ) {
            list1.add( nodeMapper.toResponseDTO( node ) );
        }

        return list1;
    }
}
