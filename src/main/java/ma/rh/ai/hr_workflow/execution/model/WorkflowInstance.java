package ma.rh.ai.hr_workflow.execution.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.Workflow;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "workflow_instances")
public class WorkflowInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which workflow is being executed (the blueprint)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_id", nullable = false)
    private Workflow workflow;

    /**
     * Who triggered this execution
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by_id", nullable = false)
    private User triggeredBy;

    /**
     * Current node being executed
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_node_id")
    private Node currentNode;

    /**
     * Current status of this instance
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WorkflowInstanceStatus status = WorkflowInstanceStatus.PENDING;

    /**
     * Individual node execution results
     */
    @OneToMany(mappedBy = "workflowInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<NodeInstance> nodeInstances = new ArrayList<>();

    /**
     * When this instance started
     */
    private LocalDateTime startedAt;

    /**
     * When this instance completed (success or failure)
     */
    private LocalDateTime finishedAt;

    /**
     * Total duration in milliseconds
     */
    private Long durationMs;

    /**
     * Input data provided when triggering (JSON)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String inputData;

    /**
     * Final output/result (JSON)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String outputData;

    /**
     * Error message if instance failed
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Stack trace if instance failed
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorStackTrace;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Calculate and set duration when instance completes
     */
    public void complete() {
        this.finishedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
        }
    }

    /**
     * Mark instance as started
     */
    public void start() {
        this.startedAt = LocalDateTime.now();
        this.status = WorkflowInstanceStatus.RUNNING;
    }
}
