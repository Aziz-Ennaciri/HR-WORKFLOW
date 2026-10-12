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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.rh.ai.hr_workflow.user.model.User;
import ma.rh.ai.hr_workflow.workflow.model.Node;
import ma.rh.ai.hr_workflow.workflow.model.NodeType;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "node_instances")
public class NodeInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    /** The workflow node this step was created from; null once that node is deleted (history is kept). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "node_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Node node;

    /** Node type frozen at trigger time. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeType nodeType;

    /** Node config frozen at trigger time: edits to the workflow never affect a running execution. */
    @Column(columnDefinition = "TEXT")
    private String configSnapshot;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_id")
    private User assignedTo;

    private Integer executionOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NodeInstanceStatus status = NodeInstanceStatus.PENDING;

    private String actor;

    @Column(columnDefinition = "TEXT")
    private String comment;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Long durationMs;

    @Column(columnDefinition = "TEXT")
    private String inputData;

    @Column(columnDefinition = "TEXT")
    private String outputData;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Integer retryCount = 0;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void complete() {
        this.finishedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.durationMs = java.time.Duration.between(startedAt, finishedAt).toMillis();
        }
    }

    public void start() {
        this.startedAt = LocalDateTime.now();
        this.status = NodeInstanceStatus.IN_PROGRESS;
    }

    public void markCompleted(String output) {
        this.status = NodeInstanceStatus.COMPLETED;
        this.outputData = (output != null && !output.isBlank()) ? output : this.inputData;
        complete();
    }

    public void markRejected(String actor, String comment) {
        this.status = NodeInstanceStatus.REJECTED;
        this.actor = actor;
        this.comment = comment;
        complete();
    }

    /** Copies the node's current type and config so this step runs exactly what was triggered. */
    public void snapshot(Node source) {
        this.node = source;
        this.nodeType = source.getType();
        this.configSnapshot = source.getConfigJson();
    }

    public void markFailed(String error) {
        this.status = NodeInstanceStatus.FAILED;
        this.errorMessage = error;
        complete();
    }
}