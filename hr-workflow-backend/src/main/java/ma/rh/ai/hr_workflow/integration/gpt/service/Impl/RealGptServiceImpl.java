package ma.rh.ai.hr_workflow.integration.gpt.service.Impl;

import java.time.LocalDateTime;
import java.util.*;

import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";

    @Override
    public String analyze(String configJson, String inputData) throws Exception {
        try {
            log.info("🤖 GPT: Analyzing with Ollama...");

            GptConfigDTO config = objectMapper.readValue(configJson, GptConfigDTO.class);
            String model = config.getModel() != null ? config.getModel() : "llama3.2:3b";

            String finalPrompt = buildPrompt(inputData);
            log.info("📝 Final prompt sent to Ollama ({} characters)", finalPrompt.length());

            ObjectNode ollamaRequest = objectMapper.createObjectNode();
            ollamaRequest.put("model", model);
            ollamaRequest.put("prompt", finalPrompt);
            ollamaRequest.put("stream", false);

            if (config.getTemperature() != null) {
                ObjectNode options = objectMapper.createObjectNode();
                options.put("temperature", config.getTemperature());
                ollamaRequest.set("options", options);
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(ollamaRequest.toString(), headers);

            String ollamaResponse = restTemplate.postForObject(OLLAMA_URL, entity, String.class);
            JsonNode responseNode = objectMapper.readTree(ollamaResponse);

            String analysis = responseNode.get("response").asText();
            int tokensUsed = responseNode.has("eval_count") ? responseNode.get("eval_count").asInt() : 0;

            log.info("✅ Ollama: Analysis complete - {} tokens", tokensUsed);
            log.info("📤 Ollama raw output: {}", analysis);

            String cleanedAnalysis = extractJsonFromText(analysis);
            if (cleanedAnalysis == null || cleanedAnalysis.isBlank()) {
                log.info("ℹ️ No JSON array found — keeping raw text response");
                cleanedAnalysis = analysis.trim();
            }

            log.info("✅ Final analysis: {}", cleanedAnalysis);

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

    /**
     * Build the Ollama prompt from inputData.
     *
     * The DRIVE node now outputs a combined JSON:
     * {
     *   "originalInput": { "prompt": "user text..." }   ← from execute page
     *   "cvData": { "totalCVs": N, "cvs": [...] }       ← from DRIVE node
     * }
     *
     * We extract both and build a clean prompt for Ollama.
     */
    private String buildPrompt(String inputData) {
        try {
            if (inputData == null || inputData.isBlank()) {
                return "No input data provided.";
            }

            // ── Try to parse the combined DRIVE output ──────────────────────────
            if (inputData.trim().startsWith("{")) {
                try {
                    JsonNode root = objectMapper.readTree(inputData.trim());

                    // Extract originalInput (what the user typed on execute page)
                    JsonNode originalInput = root.get("originalInput");

                    // Extract cvData (what DRIVE node read)
                    JsonNode cvData = root.get("cvData");

                    if (originalInput != null && cvData != null) {
                        // This is the combined DRIVE output — most common case
                        log.info("✅ Mode: Combined DRIVE output detected");
                        return buildCombinedPrompt(originalInput, cvData);
                    }

                    // Plain {"prompt": "..."} without CVs (no DRIVE node)
                    if (root.has("prompt") && !root.get("prompt").asText().isBlank()) {
                        log.info("✅ Mode: Plain-text prompt (no CV data)");
                        String userPrompt = root.get("prompt").asText().trim();
                        return "You are an HR assistant. The user says:\n\n\"" + userPrompt
                                + "\"\n\nRespond clearly and helpfully.";
                    }

                } catch (Exception e) {
                    log.warn("⚠️ Could not parse inputData as JSON: {}", e.getMessage());
                }
            }

            // ── Legacy: FILTERING_CRITERIA: format ──────────────────────────────
            if (inputData.contains("FILTERING_CRITERIA:")) {
                log.info("✅ Mode: Legacy structured criteria");
                return buildLegacyStructuredPrompt(inputData);
            }

            // ── Fallback ─────────────────────────────────────────────────────────
            log.info("⚙️ Mode: Raw fallback");
            return "You are an HR assistant. Here is a request:\n\n" + inputData
                    + "\n\nRespond helpfully and concisely.";

        } catch (Exception e) {
            log.error("❌ Error building prompt: {}", e.getMessage(), e);
            return "Analyze this: " + inputData;
        }
    }

    /**
     * Build prompt from combined DRIVE output.
     * originalInput can be {"prompt":"..."} or {"Profile":"...","Skills":"...",...}
     */
    private String buildCombinedPrompt(JsonNode originalInput, JsonNode cvData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an HR assistant. Your job is to analyze CVs and answer the user's request.\n\n");

        // ── Case A: user typed a free-text prompt ──────────────────────────────
        if (originalInput.has("prompt")) {
            String userPrompt = originalInput.get("prompt").asText().trim();
            log.info("💬 User prompt: {}", userPrompt);

            prompt.append("=== USER REQUEST ===\n");
            prompt.append(userPrompt).append("\n\n");
            prompt.append("=== INSTRUCTIONS ===\n");
            prompt.append("Read the CVs below and answer the user's request.\n");
            prompt.append("Return ONLY a valid JSON array sorted by relevance score (highest first).\n");
            prompt.append("No markdown, no explanation — JSON array only.\n");
            prompt.append("Format: [{\"name\":\"...\", \"email\":\"...\", \"experience\": number, \"skills\":\"...\", \"score\": number}]\n\n");

            // ── Case B: user sent structured criteria JSON ─────────────────────────
        } else {
            String profile    = originalInput.has("Profile")    ? originalInput.get("Profile").asText()    : "";
            String experience = originalInput.has("Experience") ? originalInput.get("Experience").asText() : "";
            String skills     = originalInput.has("Skills")     ? originalInput.get("Skills").asText()     : "";
            String topN       = originalInput.has("TopN")       ? originalInput.get("TopN").asText()       : "5";

            log.info("📋 Structured criteria — Profile:{} Exp:{} Skills:{} TopN:{}", profile, experience, skills, topN);

            prompt.append("=== JOB REQUIREMENTS ===\n");
            if (!profile.isEmpty())    prompt.append("- Target Profile     : ").append(profile).append("\n");
            if (!experience.isEmpty()) prompt.append("- Minimum Experience : ").append(experience).append(" years\n");
            if (!skills.isEmpty())     prompt.append("- Required Skills (at least one): ").append(skills).append("\n");
            prompt.append("- Return top ").append(topN).append(" candidates\n\n");

            prompt.append("=== STRICT RULES ===\n");
            if (!experience.isEmpty())
                prompt.append("1. EXCLUDE candidates with less than ").append(experience).append(" years of experience.\n");
            if (!skills.isEmpty())
                prompt.append("2. EXCLUDE candidates with none of these skills: ").append(skills).append(".\n");
            prompt.append("3. Score each candidate 0–5 based on match quality.\n");
            prompt.append("4. Sort by score DESCENDING.\n");
            prompt.append("5. Return ONLY the top ").append(topN).append(" candidates.\n");
            prompt.append("6. Output ONLY a valid JSON array — no markdown, no explanation.\n");
            prompt.append("7. Format: [{\"name\":\"...\", \"email\":\"...\", \"experience\": number, \"skills\":\"...\", \"score\": number}]\n\n");
        }

        // ── Append CV data ──────────────────────────────────────────────────────
        prompt.append("=== CANDIDATE CVs ===\n");
        prompt.append(cvData.toString()).append("\n\n");
        prompt.append("=== YOUR RESPONSE (JSON array only, nothing else) ===\n");

        return prompt.toString();
    }

    /**
     * Legacy mode: inputData uses FILTERING_CRITERIA: / CANDIDATE_DATA: sections
     */
    private String buildLegacyStructuredPrompt(String inputData) {
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

            StringBuilder prompt = new StringBuilder();
            prompt.append("You are an HR assistant. Filter and rank candidates based on these rules.\n\n");
            prompt.append("=== JOB REQUIREMENTS ===\n");
            if (!profile.isEmpty())    prompt.append("- Profile    : ").append(profile).append("\n");
            if (!experience.isEmpty()) prompt.append("- Min Exp    : ").append(experience).append(" years\n");
            if (!skills.isEmpty())     prompt.append("- Skills     : ").append(skills).append("\n");
            prompt.append("- Return top ").append(topN).append(" candidates\n\n");
            prompt.append("=== STRICT RULES ===\n");
            if (!experience.isEmpty())
                prompt.append("1. EXCLUDE candidates with less than ").append(experience).append(" years.\n");
            if (!skills.isEmpty())
                prompt.append("2. EXCLUDE candidates missing all of: ").append(skills).append(".\n");
            prompt.append("3. Score 0–5, sort descending, return top ").append(topN).append(".\n");
            prompt.append("4. Output ONLY a JSON array, no markdown.\n");
            prompt.append("   Format: [{\"name\":\"...\",\"email\":\"...\",\"experience\":number,\"skills\":\"...\",\"score\":number}]\n\n");
            prompt.append("=== CVs ===\n");
            if (candidateData != null) prompt.append(candidateData.trim());
            prompt.append("\n\n=== YOUR RESPONSE (JSON array only) ===\n");
            return prompt.toString();

        } catch (Exception e) {
            log.error("❌ Error in legacy prompt: {}", e.getMessage(), e);
            return "Analyze: " + inputData;
        }
    }

    private String extractSection(String inputData, String sectionPrefix) {
        if (inputData == null) return null;
        int start = inputData.indexOf(sectionPrefix);
        if (start == -1) return null;
        start += sectionPrefix.length();
        int end = inputData.indexOf("\n\n", start);
        if (end == -1) end = inputData.length();
        return inputData.substring(start, end).trim();
    }

    private String extractJsonFromText(String text) {
        if (text == null) return null;
        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        int startIndex = text.indexOf('[');
        if (startIndex == -1) return null;

        int braceCount = 0, endIndex = -1;
        for (int i = startIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') braceCount++;
            else if (c == ']') braceCount--;
            if (braceCount == 0) { endIndex = i + 1; break; }
        }

        return endIndex != -1 ? text.substring(startIndex, endIndex) : null;
    }
}