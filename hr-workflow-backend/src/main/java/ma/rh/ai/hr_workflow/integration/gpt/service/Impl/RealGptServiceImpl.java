package ma.rh.ai.hr_workflow.integration.gpt.service.Impl;

import java.time.LocalDateTime;
import java.util.*;

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

            log.info("🤖 GPT: provider={}", provider);

            String finalPrompt = buildPrompt(inputData);
            log.info("📝 Prompt ({} chars). Preview: {}", finalPrompt.length(),
                    finalPrompt.substring(0, Math.min(200, finalPrompt.length())));

            // Route to the right provider
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

            String cleanedAnalysis = extractJsonFromText(analysis);
            if (cleanedAnalysis == null || cleanedAnalysis.isBlank()) {
                cleanedAnalysis = analysis.trim();
            }

            GptResponseDTO response = new GptResponseDTO();
            response.setAnalysis(cleanedAnalysis);
            response.setModel(model);
            response.setTokensUsed(tokensUsed);
            response.setAnalyzedAt(LocalDateTime.now());

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

        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", model);
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
            systemMsg.put("content", "You are a strict HR recruitment assistant. Always respond with valid JSON only.");
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

            request.put("system", "You are a strict HR recruitment assistant. Always respond with valid JSON only.");
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

    private String buildPrompt(String inputData) {
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
                    log.info("✅ Mode: Plain prompt (no CVs)");
                    return "You are an HR assistant. The user says:\n\n\""
                            + root.get("prompt").asText().trim()
                            + "\"\n\nRespond clearly and helpfully.";
                }
            } catch (Exception e) {
                log.warn("⚠️ JSON parse failed: {}", e.getMessage());
            }
        }

        if (inputData.contains("FILTERING_CRITERIA:")) {
            log.info("✅ Mode: Legacy");
            return buildLegacyPrompt(inputData);
        }

        log.info("⚙️ Mode: Fallback");
        return "You are an HR assistant. Here is a request:\n\n" + inputData
                + "\n\nRespond helpfully and concisely.";
    }

    private String buildCombinedPrompt(JsonNode originalInput, JsonNode cvData) {
        StringBuilder p = new StringBuilder();

        if (originalInput.has("prompt")) {
            buildFreeTextPrompt(p, originalInput.get("prompt").asText().trim());
        } else {
            buildStructuredPrompt(p, originalInput);
        }

        p.append("=== CANDIDATE CVs ===\n");
        p.append(cvData.toString()).append("\n\n");
        p.append("=== YOUR RESPONSE (JSON array only, nothing else) ===\n");

        return p.toString();
    }

    private void buildFreeTextPrompt(StringBuilder p, String userPrompt) {
        log.info("💬 Free-text prompt: {}", userPrompt);

        p.append("You are a strict HR recruitment assistant.\n\n");

        p.append("=== USER REQUEST ===\n");
        p.append(userPrompt).append("\n\n");

        p.append("=== WHAT YOU MUST DO ===\n");
        p.append("Step 1: Read the user's request above carefully.\n");
        p.append("Step 2: Extract the criteria — what role, how many years of experience, what skills, etc.\n");
        p.append("Step 3: Read each CV below.\n");
        p.append("Step 4: For each candidate, check if they match ALL the criteria.\n");
        p.append("Step 5: REMOVE any candidate who does NOT match. Examples:\n");
        p.append("  - User says '7+ years' → remove anyone with less than 7 years\n");
        p.append("  - User says 'Java developer' → remove anyone who is not a Java developer\n");
        p.append("  - User says 'knows Kubernetes' → remove anyone without Kubernetes\n");
        p.append("Step 6: Score the remaining candidates 0-10 (10 = perfect match).\n");
        p.append("Step 7: Sort by score, highest first.\n\n");

        p.append("=== OUTPUT FORMAT ===\n");
        p.append("Return ONLY a JSON array. No text before, no text after, no markdown.\n");
        p.append("If nobody matches, return: []\n");
        p.append("Format: [{\"name\":\"...\",\"email\":\"...\",\"experience\":number,\"skills\":\"...\",\"score\":number}]\n\n");

        p.append("WARNING: Do NOT include candidates who fail the criteria. ONLY return matching candidates.\n\n");
    }

    private void buildStructuredPrompt(StringBuilder p, JsonNode criteria) {
        String profile    = txt(criteria, "Profile");
        String experience = txt(criteria, "Experience");
        String skills     = txt(criteria, "Skills");
        String topN       = criteria.has("TopN") ? criteria.get("TopN").asText() : "5";

        log.info("📋 Structured — Profile:{} Exp:{} Skills:{} TopN:{}", profile, experience, skills, topN);

        p.append("You are a strict HR recruitment assistant.\n\n");

        p.append("=== JOB REQUIREMENTS ===\n");
        if (!profile.isEmpty())    p.append("- Role       : ").append(profile).append("\n");
        if (!experience.isEmpty()) p.append("- Min Years  : ").append(experience).append("\n");
        if (!skills.isEmpty())     p.append("- Skills     : ").append(skills).append("\n");
        p.append("- Return top : ").append(topN).append(" candidates\n\n");

        p.append("=== WHAT YOU MUST DO ===\n");
        if (!experience.isEmpty())
            p.append("1. REMOVE candidates with less than ").append(experience).append(" years experience.\n");
        if (!skills.isEmpty())
            p.append("2. REMOVE candidates who have NONE of: ").append(skills).append(".\n");
        p.append("3. Score remaining candidates 0-10.\n");
        p.append("4. Sort by score, highest first.\n");
        p.append("5. Return ONLY the top ").append(topN).append(".\n");
        p.append("6. If nobody matches, return: []\n\n");

        p.append("=== OUTPUT FORMAT ===\n");
        p.append("Return ONLY a JSON array. No text, no markdown.\n");
        p.append("Format: [{\"name\":\"...\",\"email\":\"...\",\"experience\":number,\"skills\":\"...\",\"score\":number}]\n\n");

        p.append("WARNING: Do NOT include candidates who fail the criteria.\n\n");
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
                p.append("1. REMOVE candidates with less than ").append(experience).append(" years.\n");
            if (!skills.isEmpty())
                p.append("2. REMOVE candidates missing all of: ").append(skills).append(".\n");
            p.append("3. Score 0-10, sort desc, return top ").append(topN).append(".\n");
            p.append("4. If nobody matches: []\n");
            p.append("5. JSON array only, no markdown.\n");
            p.append("   [{\"name\":\"...\",\"email\":\"...\",\"experience\":number,\"skills\":\"...\",\"score\":number}]\n\n");

            p.append("=== CVs ===\n");
            if (candidateData != null) p.append(candidateData.trim());
            p.append("\n\n=== RESPONSE ===\n");
            return p.toString();

        } catch (Exception e) {
            log.error("❌ Legacy prompt error", e);
            return "Analyze: " + inputData;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  UTILS
    // ═══════════════════════════════════════════════════════════════════════════

    private String txt(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText() : "";
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

    private String extractJsonFromText(String text) {
        if (text == null) return null;
        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        int start = text.indexOf('[');
        if (start == -1) return null;

        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') depth--;
            if (depth == 0) return text.substring(start, i + 1);
        }
        return null;
    }
}