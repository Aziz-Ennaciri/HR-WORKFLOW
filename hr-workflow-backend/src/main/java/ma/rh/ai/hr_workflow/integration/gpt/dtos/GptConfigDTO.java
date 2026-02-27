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
}
