package ma.rh.ai.hr_workflow.workflow.validation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.user.repositories.UserRepository;
import ma.rh.ai.hr_workflow.workflow.model.Node;

/**
 * Checks that each node has the settings its step needs before a workflow can be activated, so
 * misconfigurations surface in the editor instead of as failed executions. Drafts may be
 * incomplete; only activation is gated.
 */
@Component
@RequiredArgsConstructor
public class NodeConfigValidator {

    private static final Set<String> PROVIDERS_WITH_KEYS = Set.of("openai", "anthropic");
    private static final Set<String> DRIVE_ACTIONS = Set.of("read", "write");

    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    /** Human-readable problems, one per missing or invalid setting; empty when the node is runnable. */
    public List<String> validate(Node node) {
        List<String> errors = new ArrayList<>();
        String label = "Step " + node.getOrderIndex() + " (" + node.getType() + ")";
        JsonNode config;
        try {
            String json = node.getConfigJson();
            config = objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            errors.add(label + ": settings are not valid JSON");
            return errors;
        }
        if (node.getType() == null) {
            errors.add(label + ": node type is missing");
            return errors;
        }

        switch (node.getType()) {
            case EMAIL -> {
                require(config, "to", label + ": recipient is required", errors);
                require(config, "subject", label + ": subject is required", errors);
            }
            case GPT -> {
                require(config, "prompt", label + ": prompt is required", errors);
                String provider = text(config, "provider");
                if (provider != null && PROVIDERS_WITH_KEYS.contains(provider.toLowerCase()) && text(config, "apiKey") == null) {
                    errors.add(label + ": an API key is required for " + provider);
                }
            }
            case DRIVE -> {
                String action = text(config, "action");
                if (action != null && !DRIVE_ACTIONS.contains(action.toLowerCase())) {
                    errors.add(label + ": action must be read or write");
                }
            }
            case EXCEL -> require(config, "fileName", label + ": file name is required", errors);
            case APPROVAL -> {
                String approver = text(config, "approverEmail");
                if (approver == null) {
                    errors.add(label + ": approver email is required");
                } else if (userRepository.findByEmail(approver).filter(User::isEnabled).isEmpty()) {
                    errors.add(label + ": approver " + approver + " is not an active user");
                }
            }
        }
        return errors;
    }

    private static void require(JsonNode config, String field, String message, List<String> errors) {
        if (text(config, field) == null) {
            errors.add(message);
        }
    }

    private static String text(JsonNode config, String field) {
        JsonNode value = config.get(field);
        return value != null && value.isValueNode() && !value.asText().isBlank() ? value.asText().trim() : null;
    }
}
