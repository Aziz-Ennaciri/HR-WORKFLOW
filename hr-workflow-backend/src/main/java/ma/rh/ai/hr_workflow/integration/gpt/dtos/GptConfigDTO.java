package ma.rh.ai.hr_workflow.integration.gpt.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GptConfigDTO {
    private String model;
    private String prompt;
    private Double temperature;
    private Integer maxTokens;

    // NEW: which provider to use — "ollama" (default), "openai", or "anthropic"
    private String provider;

    // NEW: API key for cloud providers (OpenAI, Anthropic, etc.)
    private String apiKey;

    /**
     * Returns the provider, defaulting to "ollama" if not set.
     */
    public String getEffectiveProvider() {
        if (provider == null || provider.isBlank()) return "ollama";
        return provider.trim().toLowerCase();
    }
}