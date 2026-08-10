ALTER TABLE financial_metric_snapshot
    ADD COLUMN source_concepts JSON NULL AFTER calculation_version,
    ADD COLUMN mapping_version VARCHAR(64) NULL AFTER source_concepts,
    ADD COLUMN aggregation_method VARCHAR(64) NULL AFTER mapping_version;

UPDATE financial_metric_snapshot
SET source_concepts=JSON_ARRAY(),
    mapping_version='legacy-financial-v1',
    aggregation_method='LEGACY_UNKNOWN'
WHERE mapping_version IS NULL;

ALTER TABLE financial_metric_snapshot
    MODIFY source_concepts JSON NOT NULL,
    MODIFY mapping_version VARCHAR(64) NOT NULL,
    MODIFY aggregation_method VARCHAR(64) NOT NULL;
