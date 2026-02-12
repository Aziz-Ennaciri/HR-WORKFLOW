package ma.rh.ai.hr_workflow.integration.gpt.dtos;

import java.time.LocalDateTime;

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
}
