-- Remove old foreign key
ALTER TABLE workflow_instances
    DROP CONSTRAINT IF EXISTS fknl4kgtubn557giwdiadvm4nph;

-- Add new constraint with CASCADE
ALTER TABLE workflow_instances
    ADD CONSTRAINT fknl4kgtubn557giwdiadvm4nph
        FOREIGN KEY (current_node_id)
            REFERENCES nodes(id)
            ON DELETE SET NULL;