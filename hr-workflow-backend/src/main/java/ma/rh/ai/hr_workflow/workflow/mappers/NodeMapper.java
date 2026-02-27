package ma.rh.ai.hr_workflow.workflow.mappers;

import ma.rh.ai.hr_workflow.workflow.DTOs.CreateNodeDTO;
import ma.rh.ai.hr_workflow.workflow.DTOs.NodeResponseDTO;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NodeMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "workflow", source = "workflow")
    @Mapping(target = "type", source = "dto.type")
    @Mapping(target = "orderIndex", source = "dto.order")
    @Mapping(target = "configJson", source = "dto.configJson")
    Node toEntity(CreateNodeDTO dto, Workflow workflow);

    @Mapping(target = "type", expression = "java(node.getType().name())")
    @Mapping(target = "order", source = "orderIndex")
    NodeResponseDTO toResponseDTO(Node node);

    default NodeType map(String type) {
        return NodeType.valueOf(type);
    }
}
