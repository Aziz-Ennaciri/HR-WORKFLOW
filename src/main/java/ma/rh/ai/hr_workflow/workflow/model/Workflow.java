package ma.rh.ai.hr_workflow.workflow.model;

import jakarta.persistence.*;
import ma.rh.ai.hr_workflow.user.model.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    @OneToMany(
        mappedBy = "workflow",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("orderIndex ASC")
    private List<Node> nodes = new ArrayList<>();

    private LocalDateTime createdAt;
}
