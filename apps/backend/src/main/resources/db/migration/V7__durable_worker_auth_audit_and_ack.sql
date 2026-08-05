CREATE TABLE job_run (
    id BINARY(16) NOT NULL,
    job_type VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(160) NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    attempt_count INT NOT NULL DEFAULT 0,
    max_attempts INT NOT NULL DEFAULT 5,
    scheduled_at DATETIME(6) NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_expires_at DATETIME(6) NULL,
    last_error_code VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_job_run_idempotency (idempotency_key),
    KEY ix_job_run_claim (status, scheduled_at, priority),
    KEY ix_job_run_lease (status, lease_expires_at),
    CONSTRAINT chk_job_status CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'DEAD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE job_attempt (
    id BINARY(16) NOT NULL,
    job_run_id BINARY(16) NOT NULL,
    attempt_number INT NOT NULL,
    worker_id VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    error_code VARCHAR(64) NULL,
    duration_ms BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_job_attempt_number (job_run_id, attempt_number),
    CONSTRAINT fk_job_attempt_run FOREIGN KEY (job_run_id) REFERENCES job_run (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE auth_security_event (
    id BINARY(16) NOT NULL,
    username VARCHAR(320) NULL,
    event_type VARCHAR(32) NOT NULL,
    remote_address VARCHAR(64) NULL,
    request_id VARCHAR(128) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_auth_security_rate (username, event_type, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE recommendation_acknowledgement (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    recommendation_id BINARY(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    acknowledged_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_recommendation_ack_user_key (user_id, idempotency_key),
    UNIQUE KEY uq_recommendation_ack_once (user_id, recommendation_id),
    CONSTRAINT fk_recommendation_ack_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_recommendation_ack_recommendation FOREIGN KEY (recommendation_id) REFERENCES recommendation (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
