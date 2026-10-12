-- V4 created is_deleted; V13 added the "deleted" column that the code actually uses.
DROP INDEX IF EXISTS idx_workflows_is_deleted;
ALTER TABLE workflows DROP COLUMN IF EXISTS is_deleted;

-- Workflow lists: per owner, newest first, excluding soft-deleted rows.
CREATE INDEX IF NOT EXISTS idx_workflows_owner_active ON workflows(created_by_id, created_at DESC) WHERE deleted = FALSE;

-- Execution history: per workflow, newest first; and the global newest-first list.
CREATE INDEX IF NOT EXISTS idx_workflow_instances_workflow_created ON workflow_instances(workflow_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_workflow_instances_created ON workflow_instances(created_at DESC);

-- "My approvals": steps assigned to a user in a given status.
CREATE INDEX IF NOT EXISTS idx_node_instances_assignee_status ON node_instances(assigned_to_id, status);
