package ma.rh.ai.hr_workflow.execution.mappers;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.processing.Generated;
import ma.rh.ai.hr_workflow.execution.DTOs.NodeInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-02-23T22:36:15+0000",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 17.0.18 (Ubuntu)"
)
@Component
public class NodeInstanceMapperImpl implements NodeInstanceMapper {

    @Override
    public NodeInstanceResponseDTO toResponseDTO(NodeInstance nodeInstance) {
        if ( nodeInstance == null ) {
            return null;
        }

        NodeInstanceResponseDTO nodeInstanceResponseDTO = new NodeInstanceResponseDTO();

        nodeInstanceResponseDTO.setWorkflowInstanceId( nodeInstanceWorkflowInstanceId( nodeInstance ) );
        nodeInstanceResponseDTO.setNodeId( nodeInstanceNodeId( nodeInstance ) );
        nodeInstanceResponseDTO.setId( nodeInstance.getId() );
        nodeInstanceResponseDTO.setExecutionOrder( nodeInstance.getExecutionOrder() );
        nodeInstanceResponseDTO.setActor( nodeInstance.getActor() );
        nodeInstanceResponseDTO.setComment( nodeInstance.getComment() );
        nodeInstanceResponseDTO.setStartedAt( nodeInstance.getStartedAt() );
        nodeInstanceResponseDTO.setFinishedAt( nodeInstance.getFinishedAt() );
        nodeInstanceResponseDTO.setDurationMs( nodeInstance.getDurationMs() );
        nodeInstanceResponseDTO.setInputData( nodeInstance.getInputData() );
        nodeInstanceResponseDTO.setOutputData( nodeInstance.getOutputData() );
        nodeInstanceResponseDTO.setErrorMessage( nodeInstance.getErrorMessage() );
        nodeInstanceResponseDTO.setRetryCount( nodeInstance.getRetryCount() );
        nodeInstanceResponseDTO.setCreatedAt( nodeInstance.getCreatedAt() );

        nodeInstanceResponseDTO.setNodeType( nodeInstance.getNode().getType().name() );
        nodeInstanceResponseDTO.setStatus( nodeInstance.getStatus().name() );

        return nodeInstanceResponseDTO;
    }

    @Override
    public List<NodeInstanceResponseDTO> toResponseDTO(List<NodeInstance> nodeInstances) {
        if ( nodeInstances == null ) {
            return null;
        }

        List<NodeInstanceResponseDTO> list = new ArrayList<NodeInstanceResponseDTO>( nodeInstances.size() );
        for ( NodeInstance nodeInstance : nodeInstances ) {
            list.add( toResponseDTO( nodeInstance ) );
        }

        return list;
    }

    private Long nodeInstanceWorkflowInstanceId(NodeInstance nodeInstance) {
        if ( nodeInstance == null ) {
            return null;
        }
        WorkflowInstance workflowInstance = nodeInstance.getWorkflowInstance();
        if ( workflowInstance == null ) {
            return null;
        }
        Long id = workflowInstance.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }

    private Long nodeInstanceNodeId(NodeInstance nodeInstance) {
        if ( nodeInstance == null ) {
            return null;
        }
        Node node = nodeInstance.getNode();
        if ( node == null ) {
            return null;
        }
        Long id = node.getId();
        if ( id == null ) {
            return null;
        }
        return id;
    }
}
