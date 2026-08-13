CREATE TABLE owner_preference (
    user_id BINARY(16) NOT NULL,
    emergency_cash_target DECIMAL(24,8) NULL,
    manual_execution_broker VARCHAR(32) NOT NULL DEFAULT 'FIDELITY',
    notification_preference VARCHAR(32) NOT NULL DEFAULT 'IN_APP',
    starter_buy_preference VARCHAR(32) NOT NULL DEFAULT 'STRATEGY_DEFAULT',
    primary_etf_preference VARCHAR(16) NOT NULL DEFAULT 'QQQM',
    personal_trade_risk_cap DECIMAL(18,10) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_owner_preference_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT chk_owner_preference_broker CHECK (manual_execution_broker IN ('FIDELITY','OTHER')),
    CONSTRAINT chk_owner_preference_notification CHECK (notification_preference IN ('IN_APP','EMAIL','NONE')),
    CONSTRAINT chk_owner_preference_starter CHECK (starter_buy_preference IN ('STRATEGY_DEFAULT','CONSERVATIVE','DISABLED')),
    CONSTRAINT chk_owner_preference_etf CHECK (primary_etf_preference IN ('QQQM','VTI','SPY')),
    CONSTRAINT chk_owner_preference_risk CHECK (personal_trade_risk_cap IS NULL OR (personal_trade_risk_cap BETWEEN 0.001 AND 0.01))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
