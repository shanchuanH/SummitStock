CREATE TABLE backtest_run (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    training_through DATE NULL,
    out_of_sample_from DATE NULL,
    universe_checksum CHAR(64) NOT NULL,
    config_checksum CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    bias_status VARCHAR(24) NOT NULL,
    summary_json JSON NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_backtest_user_key (user_id, idempotency_key),
    KEY ix_backtest_user_completed (user_id, completed_at),
    CONSTRAINT fk_backtest_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_backtest_status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_backtest_bias CHECK (bias_status IN ('CLEAR', 'BLOCKED', 'NOT_EVALUATED')),
    CONSTRAINT chk_backtest_period CHECK (period_end >= period_start)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE backtest_metric (
    id BINARY(16) NOT NULL,
    backtest_run_id BINARY(16) NOT NULL,
    metric_name VARCHAR(64) NOT NULL,
    metric_value DOUBLE NOT NULL,
    sample_count BIGINT NOT NULL,
    sleeve VARCHAR(16) NOT NULL DEFAULT 'ALL',
    horizon_days INT NOT NULL DEFAULT -1,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_backtest_metric_dimension (backtest_run_id, metric_name, sleeve, horizon_days),
    CONSTRAINT fk_backtest_metric_run FOREIGN KEY (backtest_run_id) REFERENCES backtest_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
