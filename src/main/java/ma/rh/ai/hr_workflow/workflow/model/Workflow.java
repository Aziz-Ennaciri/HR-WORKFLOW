package ma.rh.ai.hr_workflow.workflow.model;

import jakarta.persistence.*;
import ma.rh.ai.hr_workflow.user.model.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "workflows")
public class Workflow {
    @Id
    @GeneratedValue
    private Long id;

    private String name;

    private String description;

    private Integer version;

    @Enumerated(EnumType.STRING)
    private WorkflowStatus status;

    @ManyToOne
    private User createdBy;

    private LocalDateTime createdAt;
}
