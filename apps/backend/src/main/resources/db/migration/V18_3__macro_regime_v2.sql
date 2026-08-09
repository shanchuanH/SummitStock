CREATE TABLE macro_observation (
    id BINARY(16) NOT NULL,
    series_code VARCHAR(64) NOT NULL,
    observation_date DATE NOT NULL,
    value_decimal DECIMAL(24,10) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    quality VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_macro_observation_evidence (series_code,observation_date,provider,evidence_checksum),
    KEY ix_macro_observation_history (series_code,observation_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE macro_factor_snapshot (
    id BINARY(16) NOT NULL,
    market_date DATE NOT NULL,
    volatility_stress DECIMAL(18,10) NULL,
    credit_stress DECIMAL(18,10) NULL,
    rate_stress DECIMAL(18,10) NULL,
    curve_state VARCHAR(32) NOT NULL,
    stress_resilience DECIMAL(18,10) NULL,
    quality VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_macro_factor_evidence (market_date,evidence_checksum),
    KEY ix_macro_factor_latest (market_date),
    CONSTRAINT chk_macro_curve_state CHECK (curve_state IN ('NORMAL','FLAT','INVERTED','MISSING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
