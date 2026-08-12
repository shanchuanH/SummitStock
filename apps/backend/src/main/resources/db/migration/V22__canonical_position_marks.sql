CREATE TABLE position_mark_snapshot (
    id BINARY(16) NOT NULL,
    position_id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    quantity DECIMAL(28,10) NOT NULL,
    decision_price DECIMAL(24,8) NOT NULL,
    marked_market_value DECIMAL(24,8) NOT NULL,
    market_date DATE NOT NULL,
    source_provider VARCHAR(64) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    price_data_as_of DATETIME(6) NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    strategy_config_hash CHAR(64) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_position_mark_evidence (position_id, strategy_version, evidence_checksum),
    KEY ix_position_mark_latest (position_id, market_date DESC, data_as_of DESC, created_at DESC),
    KEY ix_position_mark_instrument_date (instrument_id, market_date DESC),
    CONSTRAINT fk_position_mark_position FOREIGN KEY (position_id) REFERENCES position (id),
    CONSTRAINT fk_position_mark_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_position_mark_values CHECK (
        quantity >= 0 AND decision_price > 0 AND marked_market_value >= 0
    ),
    CONSTRAINT chk_position_mark_quality CHECK (
        quality_status IN ('HEALTHY','PARTIAL','STALE','SUSPECT','MISSING','PARTIAL_BACKFILL')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE VIEW current_position_mark AS
SELECT id, position_id, instrument_id, quantity, decision_price, marked_market_value,
       market_date, source_provider, quality_status, price_data_as_of,
       strategy_version, strategy_config_hash, evidence_checksum, data_as_of, created_at
FROM (
    SELECT m.*,
           ROW_NUMBER() OVER (
               PARTITION BY m.position_id
               ORDER BY m.market_date DESC, m.data_as_of DESC, m.created_at DESC, m.id DESC
           ) AS rn
    FROM position_mark_snapshot m
) ranked
WHERE rn = 1;
