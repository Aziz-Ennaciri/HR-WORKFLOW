package ma.rh.ai.hr_workflow.execution.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import ma.rh.ai.hr_workflow.execution.DTOs.WorkflowInstanceResponseDTO;
import ma.rh.ai.hr_workflow.execution.model.WorkflowInstance;

@Mapper(componentModel = "spring")
public interface WorkflowInstanceMapper {

    @Mapping(target = "workflowId", source = "workflow.id")
    @Mapping(target = "workflowName", source = "workflow.name")
    @Mapping(target = "triggeredById", source = "triggeredBy.id")
    @Mapping(target = "triggeredByEmail", source = "triggeredBy.email")
    @Mapping(target = "currentNodeId", source = "currentNode.id")
    @Mapping(target = "currentNodeType", expression = "java(workflowInstance.getCurrentNode() != null ? workflowInstance.getCurrentNode().getType().name() : null)")
    @Mapping(target = "status", expression = "java(workflowInstance.getStatus().name())")
    WorkflowInstanceResponseDTO toResponseDTO(WorkflowInstance workflowInstance);

    List<WorkflowInstanceResponseDTO> toResponseDTO(List<WorkflowInstance> workflowInstances);
}