ALTER TABLE position_thesis
    ADD COLUMN thesis_progress BOOLEAN NOT NULL DEFAULT FALSE AFTER user_confirmed,
    ADD COLUMN last_evidence_at DATETIME(6) NULL AFTER confirmed_at;

CREATE TABLE investment_idea (
    id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    idea_created_at DATETIME(6) NOT NULL,
    cooldown_until DATETIME(6) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    source_reference VARCHAR(2048) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_investment_idea_cooldown (user_id, instrument_id, cooldown_until),
    CONSTRAINT fk_investment_idea_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT fk_investment_idea_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_investment_idea_source CHECK (
        source_type IN ('SOCIAL','WATCHLIST','RESEARCH','MANUAL')
    ),
    CONSTRAINT chk_investment_idea_cooldown CHECK (cooldown_until >= idea_created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
