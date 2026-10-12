-- 1) Freeze the execution plan: each node instance keeps the node type/config it was triggered with,
--    so editing or deleting a node no longer rewrites (or cascades away) execution history.
ALTER TABLE node_instances ADD COLUMN IF NOT EXISTS node_type VARCHAR(50);
ALTER TABLE node_instances ADD COLUMN IF NOT EXISTS config_snapshot TEXT;

UPDATE node_instances ni
SET node_type = n.type,
    config_snapshot = n.config_json
FROM nodes n
WHERE ni.node_id = n.id
  AND ni.node_type IS NULL;

ALTER TABLE node_instances ALTER COLUMN node_type SET NOT NULL;

ALTER TABLE node_instances ALTER COLUMN node_id DROP NOT NULL;
ALTER TABLE node_instances DROP CONSTRAINT IF EXISTS fk_node_instances_node;
ALTER TABLE node_instances
    ADD CONSTRAINT fk_node_instances_node
        FOREIGN KEY (node_id) REFERENCES nodes(id) ON DELETE SET NULL;

-- V11 added a SET NULL constraint for current_node_id but the original no-action one from V6 was never dropped.
ALTER TABLE workflow_instances DROP CONSTRAINT IF EXISTS fk_workflow_instances_current_node;

-- 2) Optimistic locking, so concurrent approve/resume/continue calls cannot both run the same execution.
ALTER TABLE workflow_instances ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE node_instances ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

