CREATE TABLE price_state_snapshot (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    market_date DATE NOT NULL,
    price_state VARCHAR(32) NOT NULL,
    reversal_confirmations INT NOT NULL,
    relative_strength DECIMAL(18,10) NULL,
    rolling_high_drawdown DECIMAL(18,10) NULL,
    strategy_version VARCHAR(64) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_price_state_evidence (instrument_id, market_date, strategy_version, evidence_checksum),
    KEY ix_price_state_latest (instrument_id, market_date),
    CONSTRAINT fk_price_state_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_price_state_confirmation CHECK (reversal_confirmations BETWEEN 0 AND 6),
    CONSTRAINT chk_price_state_value CHECK (price_state IN ('STRONG_UPTREND','UPTREND','NEUTRAL','WEAK','DOWNTREND','REVERSAL_SETUP','REVERSAL_CONFIRMED','BREAKDOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
