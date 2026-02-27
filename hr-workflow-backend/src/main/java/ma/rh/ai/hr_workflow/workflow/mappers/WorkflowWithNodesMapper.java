package ma.rh.ai.hr_workflow.workflow.mappers;

import ma.rh.ai.hr_workflow.workflow.DTOs.WorkflowWithNodesResponseDTO;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = {WorkflowMapper.class, NodeMapper.class})
public interface WorkflowWithNodesMapper {

    @Mapping(target = "workflow", source = "workflow")
    @Mapping(target = "nodes", source = "nodes")
    WorkflowWithNodesResponseDTO toDTO(Workflow workflow, List<Node> nodes);
}