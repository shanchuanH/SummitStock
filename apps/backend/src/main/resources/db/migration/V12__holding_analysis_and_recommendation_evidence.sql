ALTER TABLE position
    ADD COLUMN classification_source VARCHAR(32) NOT NULL DEFAULT 'SYSTEM_RULE';

UPDATE position
SET classification_source=IF(classification_confirmed, 'USER_CONFIRMED', 'SYSTEM_RULE');

ALTER TABLE position
    ADD CONSTRAINT chk_position_classification_source CHECK (
        classification_source IN ('SYSTEM_RULE','IMPORTED_MAPPING','USER_CONFIRMED','USER_OVERRIDE')
    );

ALTER TABLE holding_analysis_snapshot
    ADD COLUMN readiness VARCHAR(32) NULL,
    ADD COLUMN recommended_action VARCHAR(32) NULL,
    ADD COLUMN recommended_quantity_min DECIMAL(28,10) NULL,
    ADD COLUMN recommended_quantity_max DECIMAL(28,10) NULL,
    ADD COLUMN config_hash CHAR(64) NULL;

UPDATE holding_analysis_snapshot
SET readiness=CASE
        WHEN analysis_status='READY' THEN 'READY'
        WHEN analysis_status='WAIT_FOR_DATA' THEN 'WAIT_FOR_MARKET_DATA'
        ELSE 'PARTIAL'
    END,
    recommended_action=CASE
        WHEN analysis_status='WAIT_FOR_DATA' THEN 'WAIT_FOR_DATA'
        ELSE 'REVIEW'
    END,
    config_hash=REPEAT('0',64)
WHERE readiness IS NULL;

ALTER TABLE holding_analysis_snapshot
    MODIFY COLUMN readiness VARCHAR(32) NOT NULL DEFAULT 'PARTIAL',
    MODIFY COLUMN recommended_action VARCHAR(32) NOT NULL DEFAULT 'REVIEW',
    MODIFY COLUMN config_hash CHAR(64) NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000';

ALTER TABLE recommendation
    ADD COLUMN winning_rule VARCHAR(128) NULL,
    ADD COLUMN suppressed_candidates JSON NULL,
    ADD COLUMN resolution_reason VARCHAR(2048) NULL,
    ADD COLUMN config_hash CHAR(64) NULL;

UPDATE recommendation
SET winning_rule='LEGACY.RECOMMENDATION',
    suppressed_candidates=JSON_ARRAY(),
    resolution_reason='Migrated recommendation generated before conflict-resolution evidence.',
    config_hash=REPEAT('0',64)
WHERE winning_rule IS NULL;

ALTER TABLE recommendation
    MODIFY COLUMN winning_rule VARCHAR(128) NOT NULL DEFAULT 'LEGACY.RECOMMENDATION',
    MODIFY COLUMN suppressed_candidates JSON NOT NULL DEFAULT (JSON_ARRAY()),
    MODIFY COLUMN resolution_reason VARCHAR(2048) NOT NULL DEFAULT 'Legacy recommendation without conflict-resolution evidence.',
    MODIFY COLUMN config_hash CHAR(64) NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000';

CREATE TABLE instrument_analysis_profile (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    profile_type VARCHAR(32) NOT NULL,
    fund_profile_available BOOLEAN NOT NULL DEFAULT FALSE,
    thematic BOOLEAN NOT NULL DEFAULT FALSE,
    top_holding_concentration DECIMAL(18,10) NULL,
    fund_liquidity_status VARCHAR(32) NULL,
    portfolio_overlap_fraction DECIMAL(18,10) NULL,
    source VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_instrument_profile_evidence (instrument_id, profile_type, data_as_of, evidence_checksum),
    KEY ix_instrument_profile_latest (instrument_id, data_as_of),
    CONSTRAINT fk_instrument_profile_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_instrument_profile_source CHECK (source IN ('SYSTEM_RULE','IMPORTED_MAPPING','USER_CONFIRMED','USER_OVERRIDE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
