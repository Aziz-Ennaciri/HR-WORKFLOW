package ma.rh.ai.hr_workflow.integration.drive.DTOs;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DriveResponseDTO {
    private String fileId;
    private String fileName;
    private String fileUrl;
    private String webViewLink;
    private Long size;
    private LocalDateTime createdAt;
}
