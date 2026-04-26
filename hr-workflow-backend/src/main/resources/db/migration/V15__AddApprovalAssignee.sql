-- Add assigned approver to node_instances
ALTER TABLE node_instances ADD COLUMN assigned_to_id BIGINT;

-- Foreign key to users table
ALTER TABLE node_instances
    ADD CONSTRAINT fk_node_instances_assigned_to
        FOREIGN KEY (assigned_to_id) REFERENCES users(id);

-- Index for fast lookup of pending approvals per user
CREATE INDEX idx_node_instances_assigned_to ON node_instances(assigned_to_id);
CREATE INDEX idx_node_instances_status_assigned ON node_instances(status, assigned_to_id)