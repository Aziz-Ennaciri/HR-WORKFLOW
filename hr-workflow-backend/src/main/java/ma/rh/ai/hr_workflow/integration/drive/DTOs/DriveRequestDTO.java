package ma.rh.ai.hr_workflow.integration.drive.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DriveRequestDTO {
    private String content;
    private String fileName;
    private String mimeType;
}
