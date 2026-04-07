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
            log.info("📝 Final prompt ({} chars). Preview: {}", finalPrompt.length(),
                    finalPrompt.substring(0, Math.min(300, finalPrompt.length())));

            // Build Ollama request
            ObjectNode ollamaRequest = objectMapper.createObjectNode();
            ollamaRequest.put("model", model);
            ollamaRequest.put("prompt", finalPrompt);
            ollamaRequest.put("stream", false);

            ObjectNode options = objectMapper.createObjectNode();
            options.put("temperature", config.getTemperature() != null ? config.getTemperature() : 0.1);
            ollamaRequest.set("options", options);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(ollamaRequest.toString(), headers);

            String ollamaResponse = restTemplate.postForObject(OLLAMA_URL, entity, String.class);
            JsonNode responseNode = objectMapper.readTree(ollamaResponse);

            String analysis = responseNode.get("response").asText();
            int tokensUsed = responseNode.has("eval_count") ? responseNode.get("eval_count").asInt() : 0;

            log.info("✅ Ollama done — {} tokens", tokensUsed);
            log.info("📤 Raw output: {}", analysis);

            String cleanedAnalysis = extractJsonFromText(analysis);
            if (cleanedAnalysis == null || cleanedAnalysis.isBlank()) {
                log.info("ℹ️ No JSON array found — keeping raw text");
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


    private String buildPrompt(String inputData) {
        if (inputData == null || inputData.isBlank()) {
            return "No input data provided.";
        }

        // 1. Try JSON (combined DRIVE output or plain prompt)
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

        // 2. Legacy format
        if (inputData.contains("FILTERING_CRITERIA:")) {
            log.info("✅ Mode: Legacy");
            return buildLegacyPrompt(inputData);
        }

        // 3. Fallback
        log.info("⚙️ Mode: Fallback");
        return "You are an HR assistant. Here is a request:\n\n" + inputData
                + "\n\nRespond helpfully and concisely.";
    }


    private String buildCombinedPrompt(JsonNode originalInput, JsonNode cvData) {
        StringBuilder p = new StringBuilder();

        if (originalInput.has("prompt")) {
            // ── FREE TEXT: user typed whatever they want ────────────────────────
            buildFreeTextPrompt(p, originalInput.get("prompt").asText().trim());
        } else {
            // ── STRUCTURED: form-based Profile/Experience/Skills/TopN ───────────
            buildStructuredPrompt(p, originalInput);
        }

        // Append CVs
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

    // ═══════════════════════════════════════════════════════════════════════════
    //  LEGACY FORMAT  →  FILTERING_CRITERIA: / CANDIDATE_DATA:
    // ═══════════════════════════════════════════════════════════════════════════

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