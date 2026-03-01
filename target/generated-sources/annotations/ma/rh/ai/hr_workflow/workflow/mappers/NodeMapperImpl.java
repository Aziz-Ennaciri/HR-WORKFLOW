package ma.rh.ai.hr_workflow.workflow.mappers;

import javax.annotation.processing.Generated;
import ma.rh.ai.hr_workflow.workflow.DTOs.CreateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeResponseDTO;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-02-23T22:36:15+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class NodeMapperImpl implements NodeMapper {

    @Override
    public Node toEntity(CreateNodeDTO dto, Workflow workflow) {
        if ( dto == null && workflow == null ) {
            return null;
        }

        Node node = new Node();

        if ( dto != null ) {
            node.setType( map( dto.getType() ) );
            node.setOrderIndex( dto.getOrder() );
            node.setConfigJson( dto.getConfigJson() );
        }
        node.setWorkflow( workflow );

        return node;
    }

    @Override
    public NodeResponseDTO toResponseDTO(Node node) {
        if ( node == null ) {
            return null;
        }

        NodeResponseDTO nodeResponseDTO = new NodeResponseDTO();

        nodeResponseDTO.setOrder( node.getOrderIndex() );
        nodeResponseDTO.setId( node.getId() );
        nodeResponseDTO.setConfigJson( node.getConfigJson() );

        nodeResponseDTO.setType( node.getType().name() );

        return nodeResponseDTO;
    }
}
