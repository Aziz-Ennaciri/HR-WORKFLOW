package ma.rh.ai.hr_workflow.integration.email.DTOs;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EmailResponseDTO {
    private String messageId;
    private String recipient;
    private String status;
    private LocalDateTime sentAt;
}
