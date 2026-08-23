CREATE TABLE portfolio_strategy_capital_flow_event (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    effective_date DATE NOT NULL,
    amount DECIMAL(24,8) NOT NULL,
    flow_type VARCHAR(40) NOT NULL,
    source VARCHAR(64) NOT NULL,
    external_reference VARCHAR(160) NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_strategy_capital_flow_evidence (user_id,evidence_checksum),
    KEY ix_strategy_capital_flow_window (user_id,effective_date,data_as_of),
    CONSTRAINT fk_strategy_capital_flow_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_strategy_capital_flow_nonzero CHECK (amount<>0),
    CONSTRAINT chk_strategy_capital_flow_type CHECK (flow_type IN (
        'EXTERNAL_TO_STRATEGY','STRATEGY_TO_EXTERNAL','EMERGENCY_TO_STRATEGY','STRATEGY_TO_EMERGENCY'
    ))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE portfolio_cashflow_reconciliation
    ADD COLUMN prior_emergency DECIMAL(24,8) NOT NULL DEFAULT 0 AFTER broker_value,
    ADD COLUMN current_emergency DECIMAL(24,8) NOT NULL DEFAULT 0 AFTER prior_emergency,
    ADD COLUMN strategy_capital_flow DECIMAL(24,8) NOT NULL DEFAULT 0 AFTER current_emergency;
