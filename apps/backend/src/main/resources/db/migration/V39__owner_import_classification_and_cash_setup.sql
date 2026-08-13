CREATE TABLE portfolio_cash_setup (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    import_batch_id BINARY(16) NOT NULL,
    location_code VARCHAR(32) NOT NULL,
    emergency_target DECIMAL(24,8) NOT NULL,
    fidelity_emergency_amount DECIMAL(24,8) NOT NULL,
    external_emergency_amount DECIMAL(24,8) NOT NULL,
    confirmed_total DECIMAL(24,8) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cash_setup_batch (import_batch_id),
    KEY ix_cash_setup_user_created (user_id, created_at),
    CONSTRAINT fk_cash_setup_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_cash_setup_batch FOREIGN KEY (import_batch_id) REFERENCES portfolio_import_batch (id),
    CONSTRAINT chk_cash_setup_location CHECK (
        location_code IN ('IN_FIDELITY', 'EXTERNAL_BANK', 'SPLIT', 'BELOW_TARGET')
    ),
    CONSTRAINT chk_cash_setup_amounts CHECK (
        emergency_target >= 0 AND fidelity_emergency_amount >= 0
        AND external_emergency_amount >= 0 AND confirmed_total >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
