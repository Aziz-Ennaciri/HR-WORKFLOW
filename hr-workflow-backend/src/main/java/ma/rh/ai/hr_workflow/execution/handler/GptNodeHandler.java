package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.integration.gpt.service.GptService;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

@Slf4j
@Component
@RequiredArgsConstructor
public class GptNodeHandler implements NodeHandler {

    private final GptService gptService;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.GPT;
    }

    @Override
    public String execute(Node node, NodeInstance nodeInstance) throws Exception {
        String configJson = node.getConfigJson();
        String nodeInputData = nodeInstance.getInputData();
        String workflowInputData = nodeInstance.getWorkflowInstance().getInputData();

        String inputForGpt;

        if (isDriveCombinedOutput(nodeInputData)) {
            log.info("✅ GPT Handler: DRIVE already combined prompt + CVs — passing through");
            inputForGpt = nodeInputData;
        } else {
            log.info("⚠️ GPT Handler: No combined DRIVE output — using legacy merge");
            inputForGpt = legacyMerge(workflowInputData, nodeInputData);
        }

        return gptService.analyze(configJson, inputForGpt);
    }

    private boolean isDriveCombinedOutput(String data) {
        if (data == null || data.isBlank() || !data.trim().startsWith("{")) return false;
        try {
            JsonNode root = objectMapper.readTree(data.trim());
            return root.has("originalInput") && root.has("cvData");
        } catch (Exception e) {
            return false;
        }
    }

    private String legacyMerge(String workflowInput, String nodeInput) {
        StringBuilder sb = new StringBuilder();
        if (workflowInput != null && !workflowInput.isBlank())
            sb.append("FILTERING_CRITERIA:").append(workflowInput).append("\n\n");
        if (nodeInput != null && !nodeInput.isBlank())
            sb.append("CANDIDATE_DATA:").append(nodeInput);
        return sb.toString();
    }
}