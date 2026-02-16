-- Create node_instances table
CREATE TABLE IF NOT EXISTS node_instances (
    id BIGSERIAL PRIMARY KEY,
    workflow_instance_id BIGINT NOT NULL,
    node_id BIGINT NOT NULL,
    execution_order INTEGER,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    actor VARCHAR(255),
    comment TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    duration_ms BIGINT,
    input_data TEXT,
    output_data TEXT,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP,
    CONSTRAINT fk_node_instances_workflow_instance FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instances(id) ON DELETE CASCADE,
    CONSTRAINT fk_node_instances_node FOREIGN KEY (node_id) REFERENCES nodes(id)
);

-- Create indexes for better performance
CREATE INDEX idx_node_instances_workflow_instance_id ON node_instances(workflow_instance_id);
CREATE INDEX idx_node_instances_node_id ON node_instances(node_id);
CREATE INDEX idx_node_instances_status ON node_instances(status);
CREATE INDEX idx_node_instances_execution_order ON node_instances(workflow_instance_id, execution_order);