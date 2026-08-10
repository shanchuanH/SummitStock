CREATE TABLE provider_usage_daily (
    provider_id VARCHAR(64) NOT NULL,
    usage_date DATE NOT NULL,
    operation VARCHAR(64) NOT NULL,
    priority VARCHAR(2) NOT NULL,
    request_count BIGINT NOT NULL DEFAULT 0,
    success_count BIGINT NOT NULL DEFAULT 0,
    failure_count BIGINT NOT NULL DEFAULT 0,
    last_requested_at DATETIME(6) NULL,
    last_success_at DATETIME(6) NULL,
    last_error_code VARCHAR(64) NULL,
    PRIMARY KEY (provider_id, usage_date, operation),
    KEY ix_provider_usage_date_priority (usage_date, priority, provider_id),
    CONSTRAINT chk_provider_usage_priority CHECK (priority IN ('P0','P1','P2','P3','P4')),
    CONSTRAINT chk_provider_usage_counts CHECK (request_count >= 0 AND success_count >= 0 AND failure_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
