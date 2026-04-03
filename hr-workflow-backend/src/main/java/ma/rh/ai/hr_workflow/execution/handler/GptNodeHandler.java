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
        String nodeInputData = nodeInstance.getInputData(); // Data from previous node (e.g., DRIVE)
        String workflowInputData = nodeInstance.getWorkflowInstance().getInputData(); // Original workflow input (filtering criteria)
        
        // Combine both inputs for the GPT analysis
        String combinedInput = combineInputs(workflowInputData, nodeInputData);
        
        String result = gptService.analyze(configJson, combinedInput);
        
        return result;
    }

    /**
     * Combine workflow input data (filtering criteria) with node input data (candidate data)
     */
    private String combineInputs(String workflowInputData, String nodeInputData) {
        StringBuilder combined = new StringBuilder();
        
        if (workflowInputData != null && !workflowInputData.trim().isEmpty()) {
            combined.append("FILTERING_CRITERIA:").append(workflowInputData).append("\n\n");
        }
        
        if (nodeInputData != null && !nodeInputData.trim().isEmpty()) {
            combined.append("CANDIDATE_DATA:").append(nodeInputData);
        }
        
        return combined.toString();
    }
}