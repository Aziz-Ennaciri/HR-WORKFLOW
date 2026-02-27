-- Change config_json column from OID (Large Object) to TEXT
ALTER TABLE nodes ALTER COLUMN config_json TYPE TEXT;