package ma.rh.ai.hr_workflow.workflow.model;

import jakarta.persistence.*;

@Entity
@Table(name = "nodes")
public class Node {

    @Id
    @GeneratedValue
    private Long id;

    @ManyToOne
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    private NodeType type;

    private Integer orderIndex;

    @Lob
    private String configJson;
}
