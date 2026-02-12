package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.drive.service.DriveService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

@Component
@RequiredArgsConstructor
public class DriveNodeHandler implements NodeHandler {

    private final DriveService driveService;

    @Override
    public NodeType getType() {
        return NodeType.DRIVE;
    }

    @Override
    public String execute(Node node, NodeInstance nodeInstance) throws Exception {
        String configJson = node.getConfigJson();
        String inputData = nodeInstance.getInputData();
        
        String result = driveService.saveFile(configJson, inputData);
        
        return result;
    }
}