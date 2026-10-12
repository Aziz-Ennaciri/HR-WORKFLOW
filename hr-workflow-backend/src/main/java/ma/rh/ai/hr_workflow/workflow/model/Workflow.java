package ma.rh.ai.hr_workflow.workflow.model;

import jakarta.persistence.*;
import org.hibernate.Hibernate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import ma.rh.ai.hr_workflow.user.model.User;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@ToString(onlyExplicitlyIncluded = true)
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "workflows")
public class Workflow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ToString.Include
    private Long id;

    @ToString.Include
    private String name;

    private String description;

    @ToString.Include
    private Integer version;

    @Enumerated(EnumType.STRING)
    @ToString.Include
    private WorkflowStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @OneToMany(
        mappedBy = "workflow",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @OrderBy("orderIndex ASC")
    private List<Node> nodes = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String edgesJson;

    @Column(columnDefinition = "TEXT")
    private String layoutJson;

    @Column(nullable = false)
    private boolean deleted = false;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Identity is the database id; proxies compare equal to the entity they stand for. */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) return false;
        return id != null && id.equals(((Workflow) other).getId());
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
