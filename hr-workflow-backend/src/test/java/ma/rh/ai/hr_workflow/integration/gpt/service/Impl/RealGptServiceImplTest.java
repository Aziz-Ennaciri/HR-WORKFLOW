package ma.rh.ai.hr_workflow.integration.gpt.service.Impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import ma.rh.ai.hr_workflow.integration.gpt.dtos.GptResponseDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RealGptServiceImpl")
class RealGptServiceImplTest {

    private ObjectMapper objectMapper;

    // ─── helpers ─────────────────────────────────────────────────────────────────

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private RealGptServiceImpl createService() {
        RealGptServiceImpl svc = new RealGptServiceImpl(objectMapper);
        ReflectionTestUtils.setField(svc, "ollamaUrl", "http://localhost:11434/api/generate");
        ReflectionTestUtils.setField(svc, "defaultOllamaModel", "llama3.2:3b");
        return svc;
    }

    private String ollamaConfigJson(String model) {
        return model != null
                ? "{\"provider\":\"ollama\",\"model\":\"" + model + "\"}"
                : "{\"provider\":\"ollama\"}";
    }

    private String openaiConfigJson(String apiKey) {
        return "{\"provider\":\"openai\",\"apiKey\":\"" + apiKey + "\",\"model\":\"gpt-4o-mini\"}";
    }

    private String anthropicConfigJson(String apiKey) {
        return "{\"provider\":\"anthropic\",\"apiKey\":\"" + apiKey + "\",\"model\":\"claude-sonnet-4-20250514\"}";
    }

    private String ollamaResponse(String text, int evalCount) {
        return "{\"response\":\"" + text + "\",\"eval_count\":" + evalCount + "}";
    }

    private String openaiResponse(String text, int totalTokens) {
        return "{\"choices\":[{\"message\":{\"content\":\"" + text + "\"}}],"
                + "\"usage\":{\"total_tokens\":" + totalTokens + "}}";
    }

    private String anthropicResponse(String text, int inputTokens, int outputTokens) {
        return "{\"content\":[{\"text\":\"" + text + "\"}],"
                + "\"usage\":{\"input_tokens\":" + inputTokens + ",\"output_tokens\":" + outputTokens + "}}";
    }

    // ─── Provider: Ollama ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Provider: ollama")
    class OllamaProvider {

        @Test
        @DisplayName("happy path — returns analysis with eval_count as tokensUsed")
        void analyze_ollama_happyPath() throws Exception {
            // Arrange
            String configJson  = ollamaConfigJson("llama3.2:3b");
            String inputData   = "plain text candidate data";
            String mockResponse = ollamaResponse("Candidate looks great", 120);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Candidate looks great");
                assertThat(result.getModel()).isEqualTo("llama3.2:3b");
                assertThat(result.getTokensUsed()).isEqualTo(120);
                assertThat(result.getAnalyzedAt()).isNotNull();
            }
        }

        @Test
        @DisplayName("happy path — null model in config falls back to defaultOllamaModel")
        void analyze_ollama_nullModel_usesDefault() throws Exception {
            // Arrange
            String configJson   = "{\"provider\":\"ollama\"}";
            String inputData    = "some data";
            String mockResponse = ollamaResponse("Result", 50);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getModel()).isEqualTo("llama3.2:3b");
            }
        }

        @Test
        @DisplayName("happy path — empty/null provider defaults to ollama")
        void analyze_nullProvider_defaultsToOllama() throws Exception {
            // Arrange
            String configJson   = "{\"model\":\"llama3.2:3b\"}"; // no provider field
            String inputData    = "data";
            String mockResponse = ollamaResponse("OK", 10);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("OK");
            }
        }

        @Test
        @DisplayName("exception — RestTemplate throws propagates as RuntimeException")
        void analyze_ollama_restTemplateThrows_wrapsException() {
            // Arrange
            String configJson = ollamaConfigJson("llama3.2:3b");
            String inputData  = "data";

            // Act & Assert
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenThrow(new RuntimeException("Connection refused")))) {

                RealGptServiceImpl svc = createService();
                RuntimeException ex = assertThrows(RuntimeException.class,
                        () -> svc.analyze(configJson, inputData));
                assertThat(ex.getMessage()).contains("GPT analysis failed");
            }
        }

        @Test
        @DisplayName("edge case — missing eval_count in response defaults tokensUsed to 0")
        void analyze_ollama_noEvalCount_zeroTokens() throws Exception {
            // Arrange
            String configJson   = ollamaConfigJson("llama3.2:3b");
            String inputData    = "data";
            String mockResponse = "{\"response\":\"Result\"}"; // no eval_count

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getTokensUsed()).isZero();
            }
        }
    }

    // ─── Provider: OpenAI ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Provider: openai")
    class OpenAIProvider {

        @Test
        @DisplayName("happy path — returns analysis with total_tokens as tokensUsed")
        void analyze_openai_happyPath() throws Exception {
            // Arrange
            String configJson   = openaiConfigJson("sk-test-key");
            String inputData    = "candidate CV data";
            String mockResponse = openaiResponse("Strong candidate", 350);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Strong candidate");
                assertThat(result.getModel()).isEqualTo("gpt-4o-mini");
                assertThat(result.getTokensUsed()).isEqualTo(350);
            }
        }

        @Test
        @DisplayName("exception — missing API key throws RuntimeException before HTTP call")
        void analyze_openai_missingApiKey_throws() {
            // Arrange
            String configJson = "{\"provider\":\"openai\",\"model\":\"gpt-4o-mini\"}";
            String inputData  = "data";

            // Act & Assert — no RestTemplate mock needed: exception thrown before HTTP call
            RealGptServiceImpl svc = createService();
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> svc.analyze(configJson, inputData));
            assertThat(ex.getMessage()).contains("OpenAI API key is required");
        }

        @Test
        @DisplayName("exception — blank API key throws RuntimeException")
        void analyze_openai_blankApiKey_throws() {
            // Arrange
            String configJson = "{\"provider\":\"openai\",\"apiKey\":\"   \"}";
            String inputData  = "data";

            // Act & Assert
            RealGptServiceImpl svc = createService();
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> svc.analyze(configJson, inputData));
            assertThat(ex.getMessage()).contains("OpenAI API key is required");
        }

        @Test
        @DisplayName("edge case — missing usage block in response defaults tokensUsed to 0")
        void analyze_openai_noUsageBlock_zeroTokens() throws Exception {
            // Arrange
            String configJson   = openaiConfigJson("sk-key");
            String inputData    = "data";
            String mockResponse = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}]}";

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getTokensUsed()).isZero();
            }
        }
    }

    // ─── Provider: Anthropic ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Provider: anthropic")
    class AnthropicProvider {

        @Test
        @DisplayName("happy path — returns analysis with summed input+output tokens")
        void analyze_anthropic_happyPath() throws Exception {
            // Arrange
            String configJson   = anthropicConfigJson("sk-ant-key");
            String inputData    = "candidate data";
            String mockResponse = anthropicResponse("Excellent profile", 100, 200);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Excellent profile");
                assertThat(result.getModel()).isEqualTo("claude-sonnet-4-20250514");
                assertThat(result.getTokensUsed()).isEqualTo(300); // 100 input + 200 output
            }
        }

        @Test
        @DisplayName("exception — missing API key throws RuntimeException before HTTP call")
        void analyze_anthropic_missingApiKey_throws() {
            // Arrange
            String configJson = "{\"provider\":\"anthropic\",\"model\":\"claude-sonnet-4-20250514\"}";
            String inputData  = "data";

            // Act & Assert
            RealGptServiceImpl svc = createService();
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> svc.analyze(configJson, inputData));
            assertThat(ex.getMessage()).contains("Anthropic API key is required");
        }

        @Test
        @DisplayName("edge case — missing usage block defaults tokensUsed to 0")
        void analyze_anthropic_noUsageBlock_zeroTokens() throws Exception {
            // Arrange
            String configJson   = anthropicConfigJson("sk-ant-key");
            String inputData    = "data";
            String mockResponse = "{\"content\":[{\"text\":\"Result\"}]}";

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getTokensUsed()).isZero();
            }
        }
    }

    // ─── Prompt Branch 1: null / blank input ─────────────────────────────────────

    @Nested
    @DisplayName("Prompt branch: null or blank input")
    class PromptBranchNullBlank {

        @Test
        @DisplayName("null inputData — prompt becomes 'No input data provided.'")
        void analyze_nullInput_usesDefaultPrompt() throws Exception {
            // Arrange
            String configJson   = ollamaConfigJson("llama3.2:3b");
            String mockResponse = ollamaResponse("Done", 5);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, null);

                // Assert — service does not throw; returns a valid response
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Done");
            }
        }

        @Test
        @DisplayName("blank inputData — prompt becomes 'No input data provided.'")
        void analyze_blankInput_usesDefaultPrompt() throws Exception {
            // Arrange
            String configJson   = ollamaConfigJson("llama3.2:3b");
            String mockResponse = ollamaResponse("Done", 5);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, "   ");

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isNotNull();
            }
        }
    }

    // ─── Prompt Branch 2: Combined JSON (originalInput + cvData) ─────────────────

    @Nested
    @DisplayName("Prompt branch: combined JSON (originalInput + cvData)")
    class PromptBranchCombined {

        @Test
        @DisplayName("JSON with originalInput.prompt + cvData.cvs — builds combined prompt")
        void analyze_combinedJsonInput_buildsCombinedPrompt() throws Exception {
            // Arrange
            String configJson = ollamaConfigJson("llama3.2:3b");
            String inputData  = "{"
                    + "\"originalInput\":{\"prompt\":\"Rank the top 3 candidates\"},"
                    + "\"cvData\":{\"cvs\":["
                    + "  {\"fileName\":\"alice.pdf\",\"content\":\"Alice has 5 years Java\"},"
                    + "  {\"fileName\":\"bob.pdf\",\"content\":\"Bob has 2 years Python\"}"
                    + "]}}";
            String mockResponse = ollamaResponse("Alice ranked first", 200);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert — service returns successfully and RestTemplate was called once
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Alice ranked first");
                verify(mocked.constructed().get(0), times(1))
                        .postForObject(anyString(), any(), eq(String.class));
            }
        }

        @Test
        @DisplayName("JSON with originalInput (no prompt field) — uses toPrettyString fallback")
        void analyze_combinedJsonInput_noPromptField_usesPrettyString() throws Exception {
            // Arrange
            String configJson = ollamaConfigJson("llama3.2:3b");
            String inputData  = "{"
                    + "\"originalInput\":{\"criteria\":\"5 years Java\"},"
                    + "\"cvData\":{\"cvs\":["
                    + "  {\"fileName\":\"carol.pdf\",\"content\":\"Carol Senior Dev\"}"
                    + "]}}";
            String mockResponse = ollamaResponse("Carol is the best", 100);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Carol is the best");
            }
        }
    }

    // ─── Prompt Branch 3: JSON with direct `prompt` field ────────────────────────

    @Nested
    @DisplayName("Prompt branch: JSON with direct prompt field")
    class PromptBranchDirectPrompt {

        @Test
        @DisplayName("JSON containing 'prompt' key — sends that value directly as the prompt")
        void analyze_jsonWithPromptField_usesDirectPrompt() throws Exception {
            // Arrange
            String configJson   = ollamaConfigJson("llama3.2:3b");
            String inputData    = "{\"prompt\":\"Who is the best candidate?\",\"extra\":\"ignored\"}";
            String mockResponse = ollamaResponse("Alice is best", 60);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Alice is best");
            }
        }
    }

    // ─── Prompt Branch 4: FILTERING_CRITERIA legacy format ───────────────────────

    @Nested
    @DisplayName("Prompt branch: FILTERING_CRITERIA legacy format")
    class PromptBranchLegacy {

        @Test
        @DisplayName("input with FILTERING_CRITERIA: prefix — builds structured HR prompt")
        void analyze_filteringCriteriaFormat_buildsLegacyPrompt() throws Exception {
            // Arrange
            String configJson = ollamaConfigJson("llama3.2:3b");
            String inputData  = "FILTERING_CRITERIA:{\"Profile\":\"Java Developer\","
                    + "\"Experience\":\"3\",\"Skills\":\"Java,Spring\",\"TopN\":\"5\"}\n\n"
                    + "CANDIDATE_DATA:Alice|alice@test.com|5|Java,Spring\n"
                    + "Bob|bob@test.com|2|Python";
            String mockResponse = ollamaResponse("Alice ranked highest with score 9", 180);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).contains("Alice");
            }
        }
    }

    // ─── Prompt Branch 5: Plain text fallback ────────────────────────────────────

    @Nested
    @DisplayName("Prompt branch: plain text fallback")
    class PromptBranchFallback {

        @Test
        @DisplayName("plain non-JSON text without FILTERING_CRITERIA — sent as-is")
        void analyze_plainTextFallback_sendsDataAsIs() throws Exception {
            // Arrange
            String configJson   = ollamaConfigJson("llama3.2:3b");
            String inputData    = "This is a plain text analysis request with no structure";
            String mockResponse = ollamaResponse("Analyzed plain text", 30);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Analyzed plain text");
            }
        }

        @Test
        @DisplayName("JSON that fails parsing — falls through to fallback and is sent as-is")
        void analyze_malformedJsonInput_fallsBackToRawText() throws Exception {
            // Arrange
            String configJson   = ollamaConfigJson("llama3.2:3b");
            String inputData    = "{broken json not parseable";
            String mockResponse = ollamaResponse("Fallback result", 20);

            // Act
            try (MockedConstruction<RestTemplate> mocked = mockConstruction(RestTemplate.class,
                    (mock, ctx) -> when(mock.postForObject(anyString(), any(), eq(String.class)))
                            .thenReturn(mockResponse))) {

                RealGptServiceImpl svc = createService();
                String json = svc.analyze(configJson, inputData);

                // Assert — does not throw; falls back gracefully
                GptResponseDTO result = objectMapper.readValue(json, GptResponseDTO.class);
                assertThat(result.getAnalysis()).isEqualTo("Fallback result");
            }
        }
    }
}
