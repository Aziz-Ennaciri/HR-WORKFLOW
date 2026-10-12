package ma.rh.ai.hr_workflow.execution.handler;

import java.util.Optional;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

/**
 * Pauses the execution until a human approves or rejects the step.
 * The engine assigns the approver named in the config ({@code approverEmail}) when it pauses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalNodeHandler implements NodeHandler {

    public static final String APPROVAL_SIGNAL = "__WAITING_APPROVAL__";

    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.APPROVAL;
    }

    @Override
    public String execute(NodeExecutionContext context) {
        return APPROVAL_SIGNAL;
    }

    /** The approver configured on an APPROVAL node, if any. */
    public Optional<String> approverEmail(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode config = objectMapper.readTree(configJson);
            return Optional.ofNullable(config.get("approverEmail"))
                    .map(JsonNode::asText)
                    .filter(email -> !email.isBlank());
        } catch (Exception e) {
            log.warn("Failed to parse approval config: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
