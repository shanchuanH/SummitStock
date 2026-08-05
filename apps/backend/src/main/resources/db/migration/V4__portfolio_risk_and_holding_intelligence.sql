CREATE TABLE investment_account (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    account_key VARCHAR(128) NOT NULL,
    institution VARCHAR(64) NOT NULL,
    account_type VARCHAR(32) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    currency CHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_investment_account_key (user_id, account_key),
    CONSTRAINT fk_investment_account_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE equity_snapshot (
    id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL,
    total_equity DECIMAL(24,8) NOT NULL,
    cash_balance DECIMAL(24,8) NOT NULL,
    buying_power DECIMAL(24,8) NULL,
    provider VARCHAR(64) NOT NULL,
    checksum CHAR(64) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_equity_snapshot_evidence (account_id, provider, data_as_of, checksum),
    KEY ix_equity_snapshot_latest (account_id, data_as_of),
    CONSTRAINT fk_equity_snapshot_account FOREIGN KEY (account_id) REFERENCES investment_account (id),
    CONSTRAINT chk_equity_snapshot_nonnegative CHECK (total_equity >= 0 AND cash_balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE position (
    id BINARY(16) NOT NULL,
    account_id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    bucket VARCHAR(32) NOT NULL,
    classification VARCHAR(64) NOT NULL,
    classification_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    quantity DECIMAL(28,10) NOT NULL,
    average_cost DECIMAL(24,8) NOT NULL,
    market_value DECIMAL(24,8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    opened_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY ix_position_account_status (account_id, status),
    KEY ix_position_instrument (instrument_id, status),
    CONSTRAINT fk_position_account FOREIGN KEY (account_id) REFERENCES investment_account (id),
    CONSTRAINT fk_position_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_position_bucket CHECK (bucket IN ('CORE', 'TACTICAL_OVERLAY')),
    CONSTRAINT chk_position_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_position_amounts CHECK (quantity >= 0 AND average_cost >= 0 AND market_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE position_lot (
    id BINARY(16) NOT NULL,
    position_id BINARY(16) NOT NULL,
    quantity DECIMAL(28,10) NOT NULL,
    entry_price DECIMAL(24,8) NOT NULL,
    entry_date DATE NOT NULL,
    cost_basis DECIMAL(24,8) NOT NULL,
    holding_period_status VARCHAR(32) NOT NULL,
    planned_stop DECIMAL(24,8) NULL,
    initial_risk DECIMAL(24,8) NULL,
    thesis_id BINARY(16) NULL,
    source VARCHAR(64) NOT NULL,
    external_lot_key VARCHAR(128) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_position_lot_source (position_id, source, external_lot_key),
    CONSTRAINT fk_position_lot_position FOREIGN KEY (position_id) REFERENCES position (id),
    CONSTRAINT chk_position_lot_amounts CHECK (quantity > 0 AND entry_price > 0 AND cost_basis >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tax_lot (
    id BINARY(16) NOT NULL,
    position_lot_id BINARY(16) NOT NULL,
    acquired_date DATE NOT NULL,
    quantity DECIMAL(28,10) NOT NULL,
    adjusted_cost_basis DECIMAL(24,8) NOT NULL,
    holding_period_status VARCHAR(32) NOT NULL,
    wash_sale_adjustment DECIMAL(24,8) NOT NULL DEFAULT 0,
    data_as_of DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tax_lot_position_lot (position_lot_id),
    CONSTRAINT fk_tax_lot_position_lot FOREIGN KEY (position_lot_id) REFERENCES position_lot (id),
    CONSTRAINT chk_tax_lot_amounts CHECK (quantity > 0 AND adjusted_cost_basis >= 0 AND wash_sale_adjustment >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE risk_cluster (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    cluster_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    risk_cap_fraction DECIMAL(18,10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_risk_cluster_code (user_id, cluster_code),
    CONSTRAINT fk_risk_cluster_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_risk_cluster_cap CHECK (risk_cap_fraction >= 0 AND risk_cap_fraction <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE risk_cluster_membership (
    id BINARY(16) NOT NULL,
    risk_cluster_id BINARY(16) NOT NULL,
    position_id BINARY(16) NOT NULL,
    contribution_weight DECIMAL(18,10) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cluster_position (risk_cluster_id, position_id),
    CONSTRAINT fk_cluster_membership_cluster FOREIGN KEY (risk_cluster_id) REFERENCES risk_cluster (id),
    CONSTRAINT fk_cluster_membership_position FOREIGN KEY (position_id) REFERENCES position (id),
    CONSTRAINT chk_cluster_contribution CHECK (contribution_weight > 0 AND contribution_weight <= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE position_risk_snapshot (
    id BINARY(16) NOT NULL,
    position_id BINARY(16) NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    current_weight DECIMAL(18,10) NOT NULL,
    open_risk_fraction DECIMAL(18,10) NOT NULL,
    cluster_risk_fraction DECIMAL(18,10) NOT NULL,
    risk_amount DECIMAL(24,8) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_position_risk_evidence (position_id, strategy_version, data_as_of, evidence_checksum),
    KEY ix_position_risk_latest (position_id, data_as_of),
    CONSTRAINT fk_position_risk_position FOREIGN KEY (position_id) REFERENCES position (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE holding_analysis_snapshot (
    id BINARY(16) NOT NULL,
    position_id BINARY(16) NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    analysis_status VARCHAR(32) NOT NULL,
    confidence VARCHAR(32) NOT NULL,
    current_weight DECIMAL(18,10) NOT NULL,
    target_weight_min DECIMAL(18,10) NULL,
    target_weight_max DECIMAL(18,10) NULL,
    exact_quantity_allowed BOOLEAN NOT NULL,
    reasons JSON NOT NULL,
    risks JSON NOT NULL,
    change_conditions JSON NOT NULL,
    rule_ids JSON NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    valid_until DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_holding_analysis_evidence (position_id, strategy_version, data_as_of, evidence_checksum),
    KEY ix_holding_analysis_latest (position_id, data_as_of),
    CONSTRAINT fk_holding_analysis_position FOREIGN KEY (position_id) REFERENCES position (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    position_id BINARY(16) NULL,
    holding_analysis_id BINARY(16) NULL,
    strategy_version VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    priority VARCHAR(16) NOT NULL,
    quantity_min DECIMAL(28,10) NULL,
    quantity_max DECIMAL(28,10) NULL,
    target_weight_min DECIMAL(18,10) NULL,
    target_weight_max DECIMAL(18,10) NULL,
    risk_before_fraction DECIMAL(18,10) NULL,
    risk_after_fraction DECIMAL(18,10) NULL,
    confidence VARCHAR(32) NOT NULL,
    reasons JSON NOT NULL,
    risks JSON NOT NULL,
    change_conditions JSON NOT NULL,
    rule_ids JSON NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    valid_until DATETIME(6) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_recommendation_evidence (user_id, strategy_version, evidence_checksum),
    KEY ix_recommendation_today (user_id, status, priority, valid_until),
    CONSTRAINT fk_recommendation_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_recommendation_position FOREIGN KEY (position_id) REFERENCES position (id),
    CONSTRAINT fk_recommendation_analysis FOREIGN KEY (holding_analysis_id) REFERENCES holding_analysis_snapshot (id),
    CONSTRAINT chk_recommendation_priority CHECK (priority IN ('MUST_ACT', 'DO_NOT', 'WATCH', 'NORMAL')),
    CONSTRAINT chk_recommendation_status CHECK (status IN ('ACTIVE', 'ACKNOWLEDGED', 'EXPIRED', 'OVERRIDDEN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE action_decision (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    recommendation_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    decision_type VARCHAR(32) NOT NULL,
    rationale VARCHAR(2048) NULL,
    decided_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_action_decision_idempotency (user_id, idempotency_key),
    CONSTRAINT fk_action_decision_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_action_decision_recommendation FOREIGN KEY (recommendation_id) REFERENCES recommendation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
