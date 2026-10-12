-- One node instance per (execution, node): retries reset the existing row instead of inserting a new one.
-- Remove duplicates created by the old /retry endpoint, keeping the original row.
DELETE FROM node_instances ni
USING node_instances keep
WHERE ni.workflow_instance_id = keep.workflow_instance_id
  AND ni.node_id = keep.node_id
  AND ni.id > keep.id;

ALTER TABLE node_instances
    ADD CONSTRAINT uq_node_instances_instance_node UNIQUE (workflow_instance_id, node_id);
