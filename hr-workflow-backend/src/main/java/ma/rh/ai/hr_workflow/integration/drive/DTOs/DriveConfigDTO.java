package ma.rh.ai.hr_workflow.integration.drive.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DriveConfigDTO {
    private String action;
    private String folderId;
    private String fileName;
    private String mimeType;
    private boolean sharePublicly;
}
