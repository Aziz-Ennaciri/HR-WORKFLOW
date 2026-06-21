package ma.rh.ai.hr_workflow.integration.gpt.dtos;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GptResponseDTO {
    private String analysis;
    private String sentiment;
    private Double confidence;
    private String model;
    private Integer tokensUsed;
    private LocalDateTime analyzedAt;
    // Populated when outputFormat=json — the AI's response parsed as an array of objects.
    // The Excel node reads this field to render a professional structured table.
    private List<Map<String, Object>> jsonData;
}
