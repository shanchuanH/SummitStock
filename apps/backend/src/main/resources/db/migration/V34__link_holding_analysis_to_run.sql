ALTER TABLE holding_analysis_snapshot
    DROP INDEX uq_holding_analysis_evidence,
    ADD COLUMN analysis_run_id BINARY(16) NULL AFTER id,
    ADD COLUMN decision_payload JSON NULL AFTER config_hash,
    ADD KEY ix_holding_analysis_run (analysis_run_id, position_id),
    ADD CONSTRAINT fk_holding_analysis_run FOREIGN KEY (analysis_run_id) REFERENCES portfolio_analysis_run (id)
        ON DELETE SET NULL;

ALTER TABLE holding_analysis_snapshot
    ADD UNIQUE KEY uq_holding_analysis_run_position (analysis_run_id, position_id);
