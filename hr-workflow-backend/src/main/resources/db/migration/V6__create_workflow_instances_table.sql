-- Create workflow_instances table
CREATE TABLE IF NOT EXISTS workflow_instances (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    triggered_by_id BIGINT NOT NULL,
    current_node_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    duration_ms BIGINT,
    input_data TEXT,
    output_data TEXT,
    error_message TEXT,
    error_stack_trace TEXT,
    created_at TIMESTAMP,
    CONSTRAINT fk_workflow_instances_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id),
    CONSTRAINT fk_workflow_instances_triggered_by FOREIGN KEY (triggered_by_id) REFERENCES users(id),
    CONSTRAINT fk_workflow_instances_current_node FOREIGN KEY (current_node_id) REFERENCES nodes(id)
);

-- Create indexes for better performance
CREATE INDEX idx_workflow_instances_workflow_id ON workflow_instances(workflow_id);
CREATE INDEX idx_workflow_instances_triggered_by_id ON workflow_instances(triggered_by_id);
CREATE INDEX idx_workflow_instances_status ON workflow_instances(status);
CREATE INDEX idx_workflow_instances_current_node_id ON workflow_instances(current_node_id);