CREATE TABLE portfolio_allocation_snapshot (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    sleeve_code VARCHAR(32) NOT NULL,
    marked_market_value DECIMAL(24,8) NOT NULL,
    current_weight DECIMAL(12,8) NOT NULL,
    target_weight DECIMAL(12,8) NULL,
    gap_weight DECIMAL(12,8) NULL,
    primary_instrument VARCHAR(32) NULL,
    quality_status VARCHAR(32) NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    strategy_config_hash CHAR(64) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_allocation_evidence (user_id,sleeve_code,strategy_version,evidence_checksum),
    KEY ix_allocation_latest (user_id,sleeve_code,data_as_of DESC,created_at DESC),
    CONSTRAINT fk_allocation_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_allocation_values CHECK (
        marked_market_value>=0 AND current_weight>=0 AND current_weight<=1
        AND (target_weight IS NULL OR (target_weight>=0 AND target_weight<=1))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
