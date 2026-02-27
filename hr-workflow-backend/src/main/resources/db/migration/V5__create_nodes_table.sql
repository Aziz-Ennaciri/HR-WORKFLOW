-- Create nodes table
CREATE TABLE IF NOT EXISTS nodes (
    id BIGSERIAL PRIMARY KEY,
    workflow_id BIGINT NOT NULL,
    type VARCHAR(50),
    order_index INTEGER,
    config_json TEXT,
    CONSTRAINT fk_nodes_workflow FOREIGN KEY (workflow_id) REFERENCES workflows(id) ON DELETE CASCADE
);

-- Create indexes for better performance
CREATE INDEX idx_nodes_workflow_id ON nodes(workflow_id);
CREATE INDEX idx_nodes_type ON nodes(type);
CREATE INDEX idx_nodes_order ON nodes(workflow_id, order_index);