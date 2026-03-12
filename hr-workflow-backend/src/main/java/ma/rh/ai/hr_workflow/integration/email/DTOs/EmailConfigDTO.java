package ma.rh.ai.hr_workflow.integration.email.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailConfigDTO {
    private String to;
    private String cc;
    private String bcc;
    private String subject;
    private String body;
    private String templateId;
    private boolean isHtml;
}
