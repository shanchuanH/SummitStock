ALTER TABLE holding_analysis_snapshot
    ADD COLUMN evidence_refs JSON NOT NULL DEFAULT (JSON_ARRAY()) AFTER rule_ids;

ALTER TABLE recommendation
    ADD COLUMN evidence_refs JSON NOT NULL DEFAULT (JSON_ARRAY()) AFTER rule_ids;
