package ma.rh.ai.hr_workflow.integration.gpt.service.Impl;

import java.time.LocalDateTime;

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
            String prompt = config.getPrompt() != null ? config.getPrompt() : "Analyze this data";

            // Build Ollama request
            ObjectNode ollamaRequest = objectMapper.createObjectNode();
            ollamaRequest.put("model", model);
            ollamaRequest.put("prompt", prompt + "\n\nData: " + inputData);
            ollamaRequest.put("stream", false);

            // Add temperature if specified
            if (config.getTemperature() != null) {
                ObjectNode options = objectMapper.createObjectNode();
                options.put("temperature", config.getTemperature());
                ollamaRequest.set("options", options);
            }

            // Call Ollama
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(ollamaRequest.toString(), headers);

            String ollamaResponse = restTemplate.postForObject(OLLAMA_URL, entity, String.class);
            JsonNode responseNode = objectMapper.readTree(ollamaResponse);

            String analysis = responseNode.get("response").asText();
            int tokensUsed = responseNode.has("eval_count") ? responseNode.get("eval_count").asInt() : 0;

            log.info("✅ Ollama: Analysis complete - {} tokens", tokensUsed);

            // Build response
            GptResponseDTO response = new GptResponseDTO();
            response.setAnalysis(analysis);
            response.setModel(model);
            response.setTokensUsed(tokensUsed);
            response.setAnalyzedAt(LocalDateTime.now());  // ✅ FIXED

            return objectMapper.writeValueAsString(response);

        } catch (Exception e) {
            log.error("❌ GPT service failed", e);
            throw new RuntimeException("GPT analysis failed: " + e.getMessage(), e);
        }
    }
}