CREATE TABLE earnings_event (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    event_at DATETIME(6) NOT NULL,
    market_date DATE NOT NULL,
    timing VARCHAR(16) NOT NULL,
    fiscal_period VARCHAR(32) NULL,
    binary_event BOOLEAN NOT NULL DEFAULT FALSE,
    source VARCHAR(64) NOT NULL,
    source_url VARCHAR(1024) NULL,
    quality VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_earnings_event_evidence (instrument_id, event_at, source, evidence_checksum),
    KEY ix_earnings_event_calendar (instrument_id, market_date),
    CONSTRAINT fk_earnings_event_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_earnings_event_timing CHECK (timing IN ('BEFORE_OPEN','AFTER_CLOSE','DURING_MARKET','UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE earnings_reaction_snapshot (
    id BINARY(16) NOT NULL,
    earnings_event_id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    pre_close DECIMAL(24,8) NOT NULL,
    next_open DECIMAL(24,8) NOT NULL,
    next_close DECIMAL(24,8) NOT NULL,
    return_1d DECIMAL(18,10) NULL,
    return_3d DECIMAL(18,10) NULL,
    return_5d DECIMAL(18,10) NULL,
    gap_return DECIMAL(18,10) NULL,
    volume_shock DECIMAL(18,10) NULL,
    pre_event_20d_return DECIMAL(18,10) NULL,
    quality VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_earnings_reaction_event (earnings_event_id, evidence_checksum),
    KEY ix_earnings_reaction_history (instrument_id, data_as_of),
    CONSTRAINT fk_earnings_reaction_event FOREIGN KEY (earnings_event_id) REFERENCES earnings_event (id),
    CONSTRAINT fk_earnings_reaction_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE earnings_risk_snapshot
    ADD COLUMN event_risk VARCHAR(16) NULL AFTER event_count,
    ADD COLUMN median_abs_move_fraction DECIMAL(18,10) NULL AFTER event_risk,
    ADD COLUMN p75_abs_move_fraction DECIMAL(18,10) NULL AFTER median_abs_move_fraction,
    ADD COLUMN p90_abs_move_fraction DECIMAL(18,10) NULL AFTER p75_abs_move_fraction,
    ADD COLUMN worst_downside_gap_fraction DECIMAL(18,10) NULL AFTER p90_abs_move_fraction,
    ADD COLUMN best_upside_gap_fraction DECIMAL(18,10) NULL AFTER worst_downside_gap_fraction,
    ADD COLUMN median_pre_runup_fraction DECIMAL(18,10) NULL AFTER best_upside_gap_fraction,
    ADD CONSTRAINT chk_earnings_event_risk CHECK (event_risk IS NULL OR event_risk IN ('LOW','MEDIUM','HIGH','EXTREME'));
