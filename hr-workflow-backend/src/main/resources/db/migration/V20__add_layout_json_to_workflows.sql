-- Editor canvas layout ({ "<nodeId>": { "x": .., "y": .. } }); presentation only, ignored by the engine.
ALTER TABLE workflows ADD COLUMN IF NOT EXISTS layout_json TEXT;
