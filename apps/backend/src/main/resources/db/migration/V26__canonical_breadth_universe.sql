CREATE TABLE breadth_universe (
    id BINARY(16) NOT NULL,
    universe_code VARCHAR(32) NOT NULL,
    benchmark_symbol VARCHAR(16) NOT NULL,
    methodology VARCHAR(128) NOT NULL,
    expected_member_count INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_breadth_universe_code (universe_code),
    CONSTRAINT chk_breadth_expected_members CHECK (expected_member_count > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE breadth_universe_member (
    id BINARY(16) NOT NULL,
    universe_id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE NULL,
    source VARCHAR(128) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_breadth_member_period (universe_id, instrument_id, valid_from),
    KEY idx_breadth_member_asof (universe_id, valid_from, valid_to),
    CONSTRAINT fk_breadth_member_universe FOREIGN KEY (universe_id) REFERENCES breadth_universe (id),
    CONSTRAINT fk_breadth_member_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_breadth_member_dates CHECK (valid_to IS NULL OR valid_to >= valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE breadth_snapshot (
    id BINARY(16) NOT NULL,
    universe_id BINARY(16) NOT NULL,
    universe_code VARCHAR(32) NOT NULL,
    market_date DATE NOT NULL,
    pct_above_sma50 DECIMAL(12,8) NULL,
    pct_above_sma200 DECIMAL(12,8) NULL,
    advance_decline DECIMAL(18,8) NULL,
    member_count INT NOT NULL,
    covered_member_count INT NOT NULL,
    coverage DECIMAL(12,8) NOT NULL,
    quality VARCHAR(16) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_breadth_snapshot_evidence (universe_id, market_date, evidence_checksum),
    KEY idx_breadth_snapshot_latest (universe_code, market_date, data_as_of),
    CONSTRAINT fk_breadth_snapshot_universe FOREIGN KEY (universe_id) REFERENCES breadth_universe (id),
    CONSTRAINT chk_breadth_snapshot_quality CHECK (quality IN ('HEALTHY','PARTIAL','MISSING')),
    CONSTRAINT chk_breadth_snapshot_coverage CHECK (coverage >= 0 AND coverage <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO breadth_universe
    (id, universe_code, benchmark_symbol, methodology, expected_member_count, active, created_at, updated_at)
VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000500'), 'SP500', 'SPY',
     'POINT_IN_TIME_CONSTITUENTS', 500, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000501'), 'NASDAQ100', 'QQQ',
     'POINT_IN_TIME_CONSTITUENTS', 100, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
