package ma.rh.ai.hr_workflow.workflow.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "nodes")
public class Node {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    private NodeType type;

    private Integer orderIndex;

    @Lob
    private String configJson;
}
