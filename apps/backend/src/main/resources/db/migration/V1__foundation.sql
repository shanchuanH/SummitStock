CREATE TABLE app_user (
    id BINARY(16) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_user_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE strategy_version (
    id BINARY(16) NOT NULL,
    version_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    config_json JSON NOT NULL,
    config_hash CHAR(64) NOT NULL,
    published_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_strategy_version_code (version_code),
    UNIQUE KEY uq_strategy_config_hash (config_hash),
    CONSTRAINT chk_strategy_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE investment_policy (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    strategy_version_code VARCHAR(64) NOT NULL,
    emergency_cash_floor DECIMAL(24,8) NOT NULL,
    monthly_take_home DECIMAL(24,8) NOT NULL,
    monthly_expenses DECIMAL(24,8) NOT NULL,
    monthly_surplus DECIMAL(24,8) NOT NULL,
    include_unvested_compensation BOOLEAN NOT NULL DEFAULT FALSE,
    manual_execution_only BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_investment_policy_user_strategy (user_id, strategy_version_code),
    CONSTRAINT fk_policy_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_emergency_floor_nonnegative CHECK (emergency_cash_floor >= 0),
    CONSTRAINT chk_unvested_excluded CHECK (include_unvested_compensation = FALSE),
    CONSTRAINT chk_manual_execution CHECK (manual_execution_only = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE cash_bucket (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    account_id BINARY(16) NULL,
    bucket_type VARCHAR(32) NOT NULL,
    target_amount DECIMAL(24,8) NOT NULL,
    current_amount DECIMAL(24,8) NOT NULL,
    currency CHAR(3) NOT NULL,
    as_of DATE NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cash_bucket_scope (user_id, account_id, bucket_type),
    CONSTRAINT fk_cash_bucket_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_cash_bucket_type CHECK (bucket_type IN ('EMERGENCY', 'TACTICAL_RESERVE', 'ALLOCATED_TRADE')),
    CONSTRAINT chk_cash_amounts_nonnegative CHECK (target_amount >= 0 AND current_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE audit_log (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NULL,
    event_type VARCHAR(64) NOT NULL,
    entity_type VARCHAR(64) NOT NULL,
    entity_id VARCHAR(128) NOT NULL,
    strategy_version VARCHAR(64) NULL,
    rule_ids JSON NOT NULL,
    details JSON NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_audit_entity (entity_type, entity_id, occurred_at),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE SPRING_SESSION (
    PRIMARY_ID CHAR(36) NOT NULL,
    SESSION_ID CHAR(36) NOT NULL,
    CREATION_TIME BIGINT NOT NULL,
    LAST_ACCESS_TIME BIGINT NOT NULL,
    MAX_INACTIVE_INTERVAL INT NOT NULL,
    EXPIRY_TIME BIGINT NOT NULL,
    PRINCIPAL_NAME VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36) NOT NULL,
    ATTRIBUTE_NAME VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES BLOB NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;
