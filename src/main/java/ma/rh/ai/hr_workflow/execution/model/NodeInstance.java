package ma.rh.ai.hr_workflow.execution.model;

import java.time.LocalDateTime;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.rh.ai.hr_workflow.workflow.model.Node;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "node_instances")
public class NodeInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Which workflow instance this belongs to
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    /**
     * Which node was executed (the blueprint from workflow)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id", nullable = false)
    private Node node;

    /**
     * Order in which this node was executed
     */
    private Integer executionOrder;

    /**
     * Status of this node instance
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeInstanceStatus status = NodeInstanceStatus.PENDING;

    /**
     * User who acted on this node (for approval/rejection nodes)
     */
    private String actor;

    /**
     * Comment from the actor (for approval/rejection)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String comment;

    /**
     * When this node started executing
     */
    private LocalDateTime startedAt;

    /**
     * When this node finished
     */
    private LocalDateTime finishedAt;

    /**
     * Duration in milliseconds
     */
    private Long durationMs;

    /**
     * Input data to this node (JSON)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String inputData;

    /**
     * Output/result from this node (JSON)
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String outputData;

    /**
     * Error message if node failed
     */
    @Lob
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Number of retry attempts
     */
    private Integer retryCount = 0;

    /**
     * Created timestamp
     */
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Calculate and set duration when node completes
     */
    public void complete() {
        this.finishedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
        }
    }

    /**
     * Mark node as started
     */
    public void start() {
        this.startedAt = LocalDateTime.now();
        this.status = NodeInstanceStatus.IN_PROGRESS;
    }

    /**
     * Mark node as completed
     */
    public void markCompleted(String output) {
        this.status = NodeInstanceStatus.COMPLETED;
        this.outputData = output;
        complete();
    }

    /**
     * Mark node as rejected (for approval nodes)
     */
    public void markRejected(String actor, String comment) {
        this.status = NodeInstanceStatus.REJECTED;
        this.actor = actor;
        this.comment = comment;
        complete();
    }

    /**
     * Mark node as failed
     */
    public void markFailed(String error) {
        this.status = NodeInstanceStatus.FAILED;
        this.errorMessage = error;
        complete();
    }
}

