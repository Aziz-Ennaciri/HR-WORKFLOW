-- Remove old foreign key constraint
ALTER TABLE node_instances
    DROP CONSTRAINT IF EXISTS fkk6w0yxbiwvolcm97uh1h2a44g;

ALTER TABLE node_instances
    DROP CONSTRAINT IF EXISTS fk_node_instances_node;

-- Add new constraint with CASCADE
ALTER TABLE node_instances
    ADD CONSTRAINT fk_node_instances_node
        FOREIGN KEY (node_id)
            REFERENCES nodes(id)
            ON DELETE CASCADE;