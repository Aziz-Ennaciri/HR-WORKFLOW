package ma.rh.ai.hr_workflow.execution.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApproveNodeDTO {

    /** Ignored on input: the server sets it to the authenticated user. */
    private String actor;

    private String comment;
}