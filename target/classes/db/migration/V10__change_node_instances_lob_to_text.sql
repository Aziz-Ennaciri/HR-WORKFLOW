-- Change LOB columns to TEXT in node_instances table
ALTER TABLE node_instances ALTER COLUMN input_data TYPE TEXT;
ALTER TABLE node_instances ALTER COLUMN output_data TYPE TEXT;
ALTER TABLE node_instances ALTER COLUMN error_message TYPE TEXT;