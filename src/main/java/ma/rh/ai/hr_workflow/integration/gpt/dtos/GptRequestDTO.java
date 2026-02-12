package ma.rh.ai.hr_workflow.integration.gpt.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GptRequestDTO {
    private String text;
    private String context;
}
