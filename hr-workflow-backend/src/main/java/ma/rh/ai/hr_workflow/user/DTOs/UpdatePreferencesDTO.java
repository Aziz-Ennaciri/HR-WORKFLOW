package ma.rh.ai.hr_workflow.user.DTOs;

import lombok.Data;

@Data
public class UpdatePreferencesDTO {
    private String theme;
    private String language;
    private Boolean emailNotificationsEnabled;
}
