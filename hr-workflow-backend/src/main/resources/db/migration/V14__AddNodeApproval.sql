ALTER TABLE node_instances DROP CONSTRAINT IF EXISTS node_instances_status_check;

-- Re-add with WAITING_APPROVAL included
ALTER TABLE node_instances
    ADD CONSTRAINT node_instances_status_check
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'REJECTED', 'FAILED', 'SKIPPED', 'RETRYING', 'WAITING_APPROVAL'));