package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.gpt.service.GptService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

@Component
@RequiredArgsConstructor
public class GptNodeHandler implements NodeHandler {

    private final GptService gptService;

    @Override
    public NodeType getType() {
        return NodeType.GPT;
    }

    @Override
    public String execute(Node node, NodeInstance nodeInstance) throws Exception {
        String configJson = node.getConfigJson();
        String inputData = nodeInstance.getInputData();
        
        String result = gptService.analyze(configJson, inputData);
        
        return result;
    }
}