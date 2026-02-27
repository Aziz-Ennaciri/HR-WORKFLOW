-- Change LOB columns to TEXT in workflow_instances table
ALTER TABLE workflow_instances ALTER COLUMN input_data TYPE TEXT;
ALTER TABLE workflow_instances ALTER COLUMN output_data TYPE TEXT;
ALTER TABLE workflow_instances ALTER COLUMN error_message TYPE TEXT;
ALTER TABLE workflow_instances ALTER COLUMN error_stack_trace TYPE TEXT;