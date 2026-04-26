package ma.rh.ai.hr_workflow.execution.handler;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.execution.model.NodeInstance;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalNodeHandler implements NodeHandler {

    public static final String APPROVAL_SIGNAL = "__WAITING_APPROVAL__";

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.APPROVAL;
    }

    @Override
    public String execute(Node node, NodeInstance nodeInstance) throws Exception {
        try {
            String configJson = node.getConfigJson();
            if (configJson != null && !configJson.isBlank()) {
                JsonNode config = objectMapper.readTree(configJson);
                String approverEmail = config.has("approverEmail") ? config.get("approverEmail").asText() : null;

                if (approverEmail != null && !approverEmail.isBlank()) {
                    userRepository.findByEmail(approverEmail)
                            .ifPresent(nodeInstance::setAssignedTo);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse approval config: {}", e.getMessage());
        }

        return APPROVAL_SIGNAL;
    }
}