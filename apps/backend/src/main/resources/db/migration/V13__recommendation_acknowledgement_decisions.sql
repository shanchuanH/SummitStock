ALTER TABLE recommendation_acknowledgement
    ADD COLUMN decision_type VARCHAR(32) NOT NULL DEFAULT 'HANDLED',
    ADD COLUMN rationale VARCHAR(2048) NULL,
    ADD CONSTRAINT chk_recommendation_ack_decision CHECK (
        decision_type IN ('HANDLED','DEFERRED','IGNORED')
    );
