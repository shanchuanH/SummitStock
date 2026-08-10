CREATE TABLE portfolio_import_batch (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    source VARCHAR(32) NOT NULL,
    filename VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    source_checksum CHAR(64) NOT NULL,
    row_count INT NOT NULL,
    valid_row_count INT NOT NULL,
    error_row_count INT NOT NULL,
    data_as_of DATETIME(6) NULL,
    confirmed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_import_source_checksum (user_id, source, source_checksum),
    KEY ix_import_user_created (user_id, created_at),
    CONSTRAINT fk_import_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_import_batch_status CHECK (status IN ('PREVIEW', 'IMPORTING', 'CONFIRMED', 'FAILED')),
    CONSTRAINT chk_import_batch_counts CHECK (
        row_count >= 0 AND valid_row_count >= 0 AND error_row_count >= 0
        AND valid_row_count + error_row_count <= row_count
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE portfolio_import_row (
    id BINARY(16) NOT NULL,
    batch_id BINARY(16) NOT NULL,
    source_row_number INT NOT NULL,
    account_name VARCHAR(128) NULL,
    account_number_masked VARCHAR(64) NULL,
    symbol VARCHAR(32) NULL,
    description VARCHAR(255) NULL,
    asset_type VARCHAR(32) NULL,
    quantity DECIMAL(28,10) NULL,
    last_price DECIMAL(24,8) NULL,
    current_value DECIMAL(24,8) NULL,
    average_cost DECIMAL(24,8) NULL,
    cost_basis DECIMAL(24,8) NULL,
    row_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    warnings JSON NOT NULL,
    raw_json JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_import_row_number (batch_id, source_row_number),
    CONSTRAINT fk_import_row_batch FOREIGN KEY (batch_id)
        REFERENCES portfolio_import_batch (id) ON DELETE CASCADE,
    CONSTRAINT chk_import_row_type CHECK (row_type IN ('HOLDING', 'CASH', 'UNVESTED_COMPENSATION', 'UNKNOWN')),
    CONSTRAINT chk_import_row_status CHECK (status IN ('VALID', 'WARNING', 'ERROR', 'IGNORED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE investment_account
    ADD COLUMN external_account_key VARCHAR(128) NULL,
    ADD COLUMN import_source VARCHAR(32) NULL,
    ADD UNIQUE KEY uq_investment_account_external (user_id, import_source, external_account_key);

ALTER TABLE position
    ADD COLUMN external_position_key VARCHAR(160) NULL,
    ADD COLUMN import_source VARCHAR(32) NULL,
    ADD COLUMN data_as_of DATETIME(6) NULL,
    MODIFY COLUMN average_cost DECIMAL(24,8) NULL,
    ADD UNIQUE KEY uq_position_external (account_id, import_source, external_position_key);

CREATE TABLE position_snapshot (
    id BINARY(16) NOT NULL,
    position_id BINARY(16) NOT NULL,
    import_batch_id BINARY(16) NULL,
    quantity DECIMAL(28,10) NOT NULL,
    average_cost DECIMAL(24,8) NULL,
    market_value DECIMAL(24,8) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    source VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_position_snapshot_evidence (position_id, data_as_of, evidence_checksum),
    CONSTRAINT fk_position_snapshot_position FOREIGN KEY (position_id) REFERENCES position (id),
    CONSTRAINT fk_position_snapshot_import FOREIGN KEY (import_batch_id) REFERENCES portfolio_import_batch (id),
    CONSTRAINT chk_position_snapshot_values CHECK (quantity >= 0 AND market_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE compensation_holding (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL,
    external_holding_key VARCHAR(160) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    description VARCHAR(255) NULL,
    classification VARCHAR(64) NOT NULL,
    classification_confirmed BOOLEAN NOT NULL,
    quantity DECIMAL(28,10) NULL,
    estimated_value DECIMAL(24,8) NULL,
    vesting_status VARCHAR(32) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    import_batch_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_compensation_external (user_id, account_id, external_holding_key),
    CONSTRAINT fk_compensation_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_compensation_account FOREIGN KEY (account_id) REFERENCES investment_account (id),
    CONSTRAINT fk_compensation_import FOREIGN KEY (import_batch_id) REFERENCES portfolio_import_batch (id),
    CONSTRAINT chk_compensation_class CHECK (
        classification = 'UNVESTED_COMPENSATION' AND classification_confirmed = TRUE
    ),
    CONSTRAINT chk_compensation_status CHECK (vesting_status IN ('UNVESTED', 'VESTED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE portfolio_analysis_run (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    import_batch_id BINARY(16) NULL,
    market_date DATE NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    data_as_of DATETIME(6) NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_analysis_import (user_id, import_batch_id),
    KEY ix_analysis_user_created (user_id, created_at),
    CONSTRAINT fk_analysis_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_analysis_import FOREIGN KEY (import_batch_id) REFERENCES portfolio_import_batch (id),
    CONSTRAINT chk_analysis_status CHECK (
        status IN ('QUEUED', 'RUNNING', 'WAITING', 'PARTIAL', 'SUCCEEDED', 'BLOCKED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE portfolio_analysis_step (
    id BINARY(16) NOT NULL,
    run_id BINARY(16) NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    depends_on VARCHAR(64) NULL,
    attempts INT NOT NULL DEFAULT 0,
    data_as_of DATETIME(6) NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_analysis_step (run_id, step_type),
    CONSTRAINT fk_analysis_step_run FOREIGN KEY (run_id)
        REFERENCES portfolio_analysis_run (id) ON DELETE CASCADE,
    CONSTRAINT chk_analysis_step_status CHECK (
        status IN ('PENDING', 'QUEUED', 'RUNNING', 'SUCCEEDED', 'BLOCKED', 'FAILED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
