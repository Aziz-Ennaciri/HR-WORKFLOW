-- Create workflows table
CREATE TABLE IF NOT EXISTS workflows (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255),
    workflow_key VARCHAR(255),
    version INTEGER,
    status VARCHAR(50),
    created_by_id BIGINT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT fk_workflows_created_by FOREIGN KEY (created_by_id) REFERENCES users(id)
);

-- Create indexes for better performance
CREATE INDEX idx_workflows_status ON workflows(status);
CREATE INDEX idx_workflows_created_by_id ON workflows(created_by_id);
CREATE INDEX idx_workflows_is_deleted ON workflows(is_deleted);
CREATE INDEX idx_workflows_workflow_key ON workflows(workflow_key);