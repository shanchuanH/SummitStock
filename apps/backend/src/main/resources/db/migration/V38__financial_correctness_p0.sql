ALTER TABLE position
    ADD COLUMN initial_entry_price DECIMAL(24,8) NULL,
    ADD COLUMN initial_stop_price DECIMAL(24,8) NULL,
    ADD COLUMN initial_risk_per_share DECIMAL(24,8) NULL,
    ADD COLUMN risk_basis_frozen_at DATETIME(6) NULL,
    ADD CONSTRAINT chk_position_initial_risk CHECK (
        initial_risk_per_share IS NULL OR
        (initial_entry_price IS NOT NULL AND initial_stop_price IS NOT NULL
         AND initial_risk_per_share > 0 AND initial_entry_price > initial_stop_price)
    );

CREATE TABLE portfolio_external_cashflow_event (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    effective_date DATE NOT NULL,
    amount DECIMAL(24,8) NOT NULL,
    source VARCHAR(64) NOT NULL,
    external_reference VARCHAR(160) NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_external_cashflow_evidence (user_id, evidence_checksum),
    KEY ix_external_cashflow_window (user_id, effective_date, data_as_of),
    CONSTRAINT fk_external_cashflow_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_external_cashflow_nonzero CHECK (amount <> 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE portfolio_nav_snapshot (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    market_date DATE NOT NULL,
    nav DECIMAL(24,10) NOT NULL,
    units DECIMAL(28,10) NOT NULL,
    external_cashflow DECIMAL(24,8) NOT NULL,
    account_equity DECIMAL(24,8) NOT NULL,
    high_water_nav DECIMAL(24,10) NOT NULL,
    drawdown_fraction DECIMAL(12,8) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_portfolio_nav_market_date (user_id, market_date),
    KEY ix_portfolio_nav_latest (user_id, market_date, data_as_of),
    CONSTRAINT fk_portfolio_nav_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_portfolio_nav_values CHECK (
        nav > 0 AND units > 0 AND account_equity > 0 AND high_water_nav > 0
        AND drawdown_fraction >= 0 AND drawdown_fraction <= 1
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
