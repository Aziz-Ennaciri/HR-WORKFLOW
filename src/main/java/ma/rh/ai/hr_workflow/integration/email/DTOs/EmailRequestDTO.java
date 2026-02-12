package ma.rh.ai.hr_workflow.integration.email.DTOs;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailRequestDTO {
    private String recipient;
    private String subject;
    private String body;
    private Map<String, String> variables;
    private boolean isHtml;
}
