package ma.rh.ai.hr_workflow.workflow.DTOs;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateNodeDTO {
    @NotNull
    private String type;

    @NotNull
    private Integer order;

    @NotNull
    private String configJson;
}
