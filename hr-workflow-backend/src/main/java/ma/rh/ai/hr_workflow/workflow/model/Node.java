package ma.rh.ai.hr_workflow.workflow.model;

import jakarta.persistence.*;
import org.hibernate.Hibernate;
import ma.rh.ai.hr_workflow.workflow.secrets.SecretConfigConverter;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "nodes")
public class Node {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    @Enumerated(EnumType.STRING)
    @ToString.Include
    private NodeType type;

    @ToString.Include
    private Integer orderIndex;

    @Column(columnDefinition = "TEXT")
    @Convert(converter = SecretConfigConverter.class)
    private String configJson;

    /** Identity is the database id; proxies compare equal to the entity they stand for. */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false;
        return id != null && id.equals(((Node) other).getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
