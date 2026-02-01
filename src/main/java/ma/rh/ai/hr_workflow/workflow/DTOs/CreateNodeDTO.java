package ma.rh.ai.hr_workflow.workflow.DTOs;

import jakarta.validation.constraints.NotNull;

public class CreateNodeDTO {
    @NotNull
    private String type;   // GPT, DRIVE, EMAIL, EXCEL

    @NotNull
    private Integer order;

    @NotNull
    private String configJson;
}
