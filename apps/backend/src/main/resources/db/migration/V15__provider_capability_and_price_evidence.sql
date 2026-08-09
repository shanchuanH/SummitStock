ALTER TABLE quote
    ADD COLUMN decision_market_date DATE NULL AFTER last_price,
    ADD COLUMN decision_quality_status VARCHAR(32) NOT NULL DEFAULT 'MISSING' AFTER quality_status,
    ADD COLUMN execution_quality_status VARCHAR(32) NOT NULL DEFAULT 'MISSING' AFTER decision_quality_status,
    ADD COLUMN spread_fraction DECIMAL(18,10) NULL AFTER execution_quality_status,
    ADD KEY ix_quote_decision_latest (instrument_id, decision_market_date, data_as_of);

UPDATE quote
SET decision_market_date = DATE(source_timestamp),
    decision_quality_status = quality_status,
    execution_quality_status = CASE
        WHEN bid_price IS NOT NULL AND ask_price IS NOT NULL AND ask_price >= bid_price THEN quality_status
        ELSE 'MISSING'
    END,
    spread_fraction = CASE
        WHEN bid_price IS NOT NULL AND ask_price IS NOT NULL AND ask_price >= bid_price
             AND (bid_price + ask_price) > 0
        THEN (ask_price - bid_price) / ((ask_price + bid_price) / 2)
        ELSE NULL
    END;

CREATE TABLE provider_capability_snapshot (
    id BINARY(16) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    capability VARCHAR(64) NOT NULL,
    availability VARCHAR(16) NOT NULL,
    reason_code VARCHAR(64) NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_capability_evidence (provider_id, capability, data_as_of),
    KEY ix_provider_capability_latest (provider_id, data_as_of DESC),
    CONSTRAINT chk_provider_capability_availability CHECK (availability IN ('REQUIRED','OPTIONAL','AVAILABLE','UNAVAILABLE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
