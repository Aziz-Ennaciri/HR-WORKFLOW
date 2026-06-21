package ma.rh.ai.hr_workflow.integration.gpt.service.Impl;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptConfigDTO;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptResponseDTO;
import ma.rh.ai.hr_workflow.integration.gpt.service.GptService;

@Slf4j
@Service
@Primary
@RequiredArgsConstructor
public class RealGptServiceImpl implements GptService {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${google.ollama.url:http://localhost:11434/api/generate}")
    private String ollamaUrl;

    @Value("${google.ollama.default.model:llama3.2:3b}")
    private String defaultOllamaModel;

    // ═══════════════════════════════════════════════════════════════════════════
    //  MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════════

    @Override
    public String analyze(String configJson, String inputData) throws Exception {
        try {
            GptConfigDTO config = objectMapper.readValue(configJson, GptConfigDTO.class);
            String provider = config.getEffectiveProvider();
            String outputFormat = config.getOutputFormat(); // "text" or "json"

            log.info("🤖 GPT: provider={}, outputFormat={}", provider, outputFormat);

            String finalPrompt = buildPrompt(inputData, outputFormat);
            log.info("📝 Prompt ({} chars). Preview: {}", finalPrompt.length(),
                    finalPrompt.substring(0, Math.min(200, finalPrompt.length())));

            String analysis;
            String model;
            int tokensUsed;

            switch (provider) {
                case "openai":
                    var openaiResult = callOpenAI(config, finalPrompt);
                    analysis = openaiResult[0];
                    model = openaiResult[1];
                    tokensUsed = Integer.parseInt(openaiResult[2]);
                    break;

                case "anthropic":
                    var anthropicResult = callAnthropic(config, finalPrompt);
                    analysis = anthropicResult[0];
                    model = anthropicResult[1];
                    tokensUsed = Integer.parseInt(anthropicResult[2]);
                    break;

                default: // "ollama"
                    var ollamaResult = callOllama(config, finalPrompt);
                    analysis = ollamaResult[0];
                    model = ollamaResult[1];
                    tokensUsed = Integer.parseInt(ollamaResult[2]);
                    break;
            }

            log.info("✅ {} done — {} tokens", provider, tokensUsed);
            log.info("📤 Raw output: {}", analysis);

            GptResponseDTO response = new GptResponseDTO();
            response.setAnalysis(analysis.trim());
            response.setModel(model);
            response.setTokensUsed(tokensUsed);
            response.setAnalyzedAt(LocalDateTime.now());

            // Always try to extract a JSON array — the Excel node reads jsonData first
            // and falls back to text formatting if the AI didn't return valid JSON.
            String extracted = extractJsonArray(analysis);
            if (extracted != null) {
                try {
                    JsonNode arr = objectMapper.readTree(extracted);
                    response.setJsonData(objectMapper.convertValue(arr,
                            new TypeReference<List<Map<String, Object>>>() {}));
                    log.info("✅ Extracted JSON array with {} items", arr.size());
                } catch (Exception ex) {
                    log.warn("⚠️ JSON array extraction parse failed: {}", ex.getMessage());
                }
            } else {
                log.warn("⚠️ AI did not return a parseable JSON array — Excel will fall back to text formatter");
            }

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("❌ GPT service failed", e);
            throw new RuntimeException("GPT analysis failed: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PROVIDER: OLLAMA (local)
    // ═══════════════════════════════════════════════════════════════════════════

    private String[] callOllama(GptConfigDTO config, String prompt) {
        String model = config.getModel() != null ? config.getModel() : defaultOllamaModel;
        log.info("🦙 Ollama: model={}", model);

        String systemPrompt = "You are an HR assistant. You MUST return ONLY a valid JSON array of objects. "
                + "No markdown code blocks (```), no explanatory text, no preamble. Start with [ and end with ].";

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
        request.put("system", systemPrompt);
        request.put("prompt", prompt);
        request.put("stream", false);

        ObjectNode options = objectMapper.createObjectNode();
        options.put("temperature", config.getTemperature() != null ? config.getTemperature() : 0.1);
        request.set("options", options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

        String responseStr = restTemplate.postForObject(ollamaUrl, entity, String.class);
        try {
            JsonNode responseNode = objectMapper.readTree(responseStr);
            String analysis = responseNode.get("response").asText();
            int tokens = responseNode.has("eval_count") ? responseNode.get("eval_count").asInt() : 0;
            return new String[]{analysis, model, String.valueOf(tokens)};
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ollama response", e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PROVIDER: OPENAI
    // ═══════════════════════════════════════════════════════════════════════════

    private String[] callOpenAI(GptConfigDTO config, String prompt) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("OpenAI API key is required. Set it in the GPT node config.");
        }

        String model = config.getModel() != null ? config.getModel() : "gpt-4o-mini";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.1;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 2000;

        log.info("🧠 OpenAI: model={}", model);

        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("model", model);
            request.put("temperature", temperature);
            request.put("max_tokens", maxTokens);

            ArrayNode messages = objectMapper.createArrayNode();

            ObjectNode systemMsg = objectMapper.createObjectNode();
            systemMsg.put("role", "system");
            systemMsg.put("content", "You are an HR assistant. You MUST return ONLY a valid JSON array of objects. "
                    + "No markdown code blocks (```), no explanatory text, no preamble. Start with [ and end with ].");
            messages.add(systemMsg);

            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);

            request.set("messages", messages);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            String responseStr = restTemplate.postForObject(
                    "https://api.openai.com/v1/chat/completions", entity, String.class);

            JsonNode responseNode = objectMapper.readTree(responseStr);
            String analysis = responseNode.get("choices").get(0).get("message").get("content").asText();
            int tokens = responseNode.has("usage") ? responseNode.get("usage").get("total_tokens").asInt() : 0;

            return new String[]{analysis, model, String.valueOf(tokens)};

        } catch (Exception e) {
            throw new RuntimeException("OpenAI call failed: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PROVIDER: ANTHROPIC (Claude)
    // ═══════════════════════════════════════════════════════════════════════════

    private String[] callAnthropic(GptConfigDTO config, String prompt) {
        String apiKey = config.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("Anthropic API key is required. Set it in the GPT node config.");
        }

        String model = config.getModel() != null ? config.getModel() : "claude-sonnet-4-20250514";
        double temperature = config.getTemperature() != null ? config.getTemperature() : 0.1;
        int maxTokens = config.getMaxTokens() != null ? config.getMaxTokens() : 2000;

        log.info("🟣 Anthropic: model={}", model);

        try {
            ObjectNode request = objectMapper.createObjectNode();
            request.put("model", model);
            request.put("max_tokens", maxTokens);

            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode userMsg = objectMapper.createObjectNode();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.add(userMsg);
            request.set("messages", messages);

            request.put("system", "You are an HR assistant. You MUST return ONLY a valid JSON array of objects. "
                    + "No markdown code blocks (```), no explanatory text, no preamble. Start with [ and end with ].");
            request.put("temperature", temperature);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", "2023-06-01");
            HttpEntity<String> entity = new HttpEntity<>(request.toString(), headers);

            String responseStr = restTemplate.postForObject(
                    "https://api.anthropic.com/v1/messages", entity, String.class);

            JsonNode responseNode = objectMapper.readTree(responseStr);
            String analysis = responseNode.get("content").get(0).get("text").asText();
            int tokens = responseNode.has("usage")
                    ? responseNode.get("usage").get("input_tokens").asInt()
                    + responseNode.get("usage").get("output_tokens").asInt()
                    : 0;

            return new String[]{analysis, model, String.valueOf(tokens)};

        } catch (Exception e) {
            throw new RuntimeException("Anthropic call failed: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  PROMPT BUILDING (same as before — works for all providers)
    // ═══════════════════════════════════════════════════════════════════════════

    private String buildPrompt(String inputData, String outputFormat) {
        String base = buildBasePrompt(inputData);
        // Always force JSON — the Excel node parses it into a structured table,
        // and falls back to plain-text formatting if the AI doesn't comply.
        return base
                + "\n\nIMPORTANT: You MUST respond ONLY with a valid JSON array. "
                + "Do not include ANY text before or after the JSON. Do not use markdown code fences (```).\n"
                + "Each item is a JSON object. Suggested fields: rank, name, email, experience, skills, score, summary.\n"
                + "Example: [{\"rank\":1,\"name\":\"Alice\",\"email\":\"alice@test.com\","
                + "\"experience\":\"5 years\",\"skills\":\"Java, Spring Boot\",\"score\":\"9/10\","
                + "\"summary\":\"Strong candidate\"}]\n";
    }

    private String buildBasePrompt(String inputData) {
        if (inputData == null || inputData.isBlank()) {
            return "No input data provided.";
        }

        if (inputData.trim().startsWith("{")) {
            try {
                JsonNode root = objectMapper.readTree(inputData.trim());

                JsonNode originalInput = root.get("originalInput");
                JsonNode cvData = root.get("cvData");

                if (originalInput != null && cvData != null) {
                    log.info("✅ Mode: Combined DRIVE output");
                    return buildCombinedPrompt(originalInput, cvData);
                }

                if (root.has("prompt") && !root.get("prompt").asText().isBlank()) {
                    return root.get("prompt").asText().trim();
                }

            } catch (Exception e) {
                log.warn("⚠️ JSON parse failed: {}", e.getMessage());
            }
        }

        if (inputData.contains("FILTERING_CRITERIA:")) {
            return buildLegacyPrompt(inputData);
        }

        log.info("⚙️ Mode: Fallback");
        return inputData;
    }

    private String extractJsonArray(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.trim();
        // Strip markdown code fences if present (```json ... ``` or ``` ... ```)
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) return null;
        String candidate = cleaned.substring(start, end + 1).trim();
        try {
            objectMapper.readTree(candidate);
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }

    private String buildCombinedPrompt(JsonNode originalInput, JsonNode cvData) {
        StringBuilder p = new StringBuilder();

        if (originalInput.has("prompt")) {
            p.append(originalInput.get("prompt").asText().trim());
        } else {
            p.append(originalInput.toPrettyString());
        }
        p.append("\n\n");

        // Data from previous node — formatted readably
        p.append("Here is the data:\n\n");
        JsonNode items = cvData.get("cvs");
        if (items != null && items.isArray()) {
            for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                p.append("--- ");
                p.append(item.has("fileName") ? item.get("fileName").asText() : "Item " + (i + 1));
                p.append(" ---\n");
                if (item.has("content")) {
                    p.append(item.get("content").asText().trim());
                }
                p.append("\n\n");
            }
        } else {
            p.append(cvData.toString()).append("\n\n");
        }

        return p.toString();
    }


    private String buildLegacyPrompt(String inputData) {
        try {
            String criteriaJson = extractSection(inputData, "FILTERING_CRITERIA:");
            String candidateData = extractSection(inputData, "CANDIDATE_DATA:");

            String profile = "", experience = "", skills = "", topN = "5";
            if (criteriaJson != null) {
                JsonNode c = objectMapper.readTree(criteriaJson.trim());
                if (c.has("Profile"))    profile    = c.get("Profile").asText();
                if (c.has("Experience")) experience = c.get("Experience").asText();
                if (c.has("Skills"))     skills     = c.get("Skills").asText();
                if (c.has("TopN"))       topN       = c.get("TopN").asText();
            }

            StringBuilder p = new StringBuilder();
            p.append("You are a strict HR recruitment assistant.\n\n");
            p.append("=== REQUIREMENTS ===\n");
            if (!profile.isEmpty())    p.append("- Role  : ").append(profile).append("\n");
            if (!experience.isEmpty()) p.append("- Min   : ").append(experience).append(" years\n");
            if (!skills.isEmpty())     p.append("- Skills: ").append(skills).append("\n");
            p.append("- Top   : ").append(topN).append("\n\n");

            p.append("=== RULES ===\n");
            if (!experience.isEmpty())
                p.append("1. Exclude candidates with less than ").append(experience).append(" years of experience.\n");
            if (!skills.isEmpty())
                p.append("2. Exclude candidates who lack all of these skills: ").append(skills).append(".\n");
            p.append("3. Score each remaining candidate 0-10 and select the top ").append(topN).append(".\n");
            p.append("4. If no candidates match, return an empty JSON array: []\n\n");
            p.append("=== OUTPUT FORMAT ===\n");
            p.append("Return ONLY a JSON array of objects with these fields: rank, name, email, experience, skills, score, summary.\n");
            p.append("Do not output any text before or after the JSON. No markdown code fences.\n\n");

            p.append("=== CVs ===\n");
            if (candidateData != null) p.append(candidateData.trim());
            p.append("\n\n=== RESPONSE ===\n");
            return p.toString();

        } catch (Exception e) {
            log.error("❌ Legacy prompt error", e);
            return "Analyze: " + inputData;
        }
    }


    private String extractSection(String data, String prefix) {
        if (data == null) return null;
        int start = data.indexOf(prefix);
        if (start == -1) return null;
        start += prefix.length();
        int end = data.indexOf("\n\n", start);
        if (end == -1) end = data.length();
        return data.substring(start, end).trim();
    }

}