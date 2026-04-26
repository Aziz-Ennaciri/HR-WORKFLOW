package ma.rh.ai.hr_workflow.execution.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ma.rh.ai.hr_workflow.execution.DTOs.NodeInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;

@Mapper(componentModel = "spring")
public interface NodeInstanceMapper {

    @Mapping(target = "workflowInstanceId", source = "workflowInstance.id")
    @Mapping(target = "nodeId", source = "node.id")
    @Mapping(target = "nodeType", expression = "java(nodeInstance.getNode().getType().name())")
    @Mapping(target = "status", expression = "java(nodeInstance.getStatus().name())")
    @Mapping(target = "assignedToId", source = "assignedTo.id")
    @Mapping(target = "assignedToEmail", source = "assignedTo.email")
    @Mapping(target = "assignedToName", expression = "java(nodeInstance.getAssignedTo() != null ? nodeInstance.getAssignedTo().getFirstName() + \" \" + nodeInstance.getAssignedTo().getLastName() : null)")
    NodeInstanceResponseDTO toResponseDTO(NodeInstance nodeInstance);

    List<NodeInstanceResponseDTO> toResponseDTO(List<NodeInstance> nodeInstances);

}