CREATE TABLE portfolio_capital_snapshot (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    strategy_config_hash CHAR(64) NOT NULL,
    invested_tradable_assets DECIMAL(24,8) NOT NULL,
    tracked_cash DECIMAL(24,8) NOT NULL,
    emergency_reserve DECIMAL(24,8) NOT NULL,
    deployable_cash DECIMAL(24,8) NOT NULL,
    investable_assets DECIMAL(24,8) NOT NULL,
    total_liquid_assets DECIMAL(24,8) NOT NULL,
    unvested_compensation_value DECIMAL(24,8) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_capital_snapshot_evidence (user_id, strategy_version, evidence_checksum),
    KEY ix_capital_snapshot_latest (user_id, data_as_of DESC, created_at DESC),
    CONSTRAINT fk_capital_snapshot_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_capital_snapshot_nonnegative CHECK (
        invested_tradable_assets >= 0 AND tracked_cash >= 0 AND emergency_reserve >= 0
        AND deployable_cash >= 0 AND investable_assets >= 0 AND total_liquid_assets >= 0
        AND unvested_compensation_value >= 0
    ),
    CONSTRAINT chk_capital_snapshot_quality CHECK (quality_status IN ('HEALTHY','PARTIAL','STALE','SUSPECT','MISSING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
