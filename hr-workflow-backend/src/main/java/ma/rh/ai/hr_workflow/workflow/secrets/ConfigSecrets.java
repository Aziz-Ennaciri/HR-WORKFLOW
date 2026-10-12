package ma.rh.ai.hr_workflow.workflow.secrets;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Secret fields inside node configs (currently the GPT provider {@code apiKey}). They are never
 * returned by the API: responses carry {@link #MASK}, and a save that sends the mask back keeps
 * the stored value.
 */
public final class ConfigSecrets {

    public static final String MASK = "********";
    static final Set<String> SECRET_FIELDS = Set.of("apiKey");

    private static final ObjectMapper JSON = new ObjectMapper();

    private ConfigSecrets() {
    }

    /** The config with every secret value replaced by {@link #MASK}. */
    public static String mask(String configJson) {
        ObjectNode config = parseObject(configJson);
        if (config == null) {
            return configJson;
        }
        boolean changed = false;
        for (String field : SECRET_FIELDS) {
            JsonNode value = config.get(field);
            if (value != null && value.isTextual() && !value.asText().isEmpty()) {
                config.put(field, MASK);
                changed = true;
            }
        }
        return changed ? config.toString() : configJson;
    }

    /**
     * Resolves masked secrets in an incoming config: a field still equal to {@link #MASK} takes
     * the value from {@code existingConfigJson} (or is dropped if there is none).
     */
    public static String keepMaskedSecrets(String incomingConfigJson, String existingConfigJson) {
        ObjectNode incoming = parseObject(incomingConfigJson);
        if (incoming == null) {
            return incomingConfigJson;
        }
        ObjectNode existing = parseObject(existingConfigJson);
        boolean changed = false;
        for (String field : SECRET_FIELDS) {
            JsonNode value = incoming.get(field);
            if (value != null && MASK.equals(value.asText())) {
                JsonNode previous = existing == null ? null : existing.get(field);
                if (previous != null && previous.isTextual()) {
                    incoming.put(field, previous.asText());
                } else {
                    incoming.remove(field);
                }
                changed = true;
            }
        }
        return changed ? incoming.toString() : incomingConfigJson;
    }

    static ObjectNode parseObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = JSON.readTree(json);
            return node instanceof ObjectNode object ? object : null;
        } catch (Exception e) {
            return null;
        }
    }
}
