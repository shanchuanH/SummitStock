CREATE TABLE worker_runtime_heartbeat (
    worker_id VARCHAR(96) NOT NULL,
    runtime_version VARCHAR(64) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    last_seen_at DATETIME(6) NOT NULL,
    host_hint VARCHAR(160) NOT NULL,
    status VARCHAR(24) NOT NULL,
    PRIMARY KEY (worker_id),
    KEY ix_worker_heartbeat_seen (last_seen_at),
    CONSTRAINT chk_worker_heartbeat_status CHECK (status IN ('ONLINE', 'STOPPING'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE portfolio_analysis_step_dependency (
    run_id BINARY(16) NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    depends_on VARCHAR(64) NOT NULL,
    PRIMARY KEY (run_id, step_type, depends_on),
    CONSTRAINT fk_analysis_dependency_run FOREIGN KEY (run_id)
        REFERENCES portfolio_analysis_run (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

