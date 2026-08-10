ALTER TABLE job_run
    ADD COLUMN analysis_run_id BINARY(16) NULL,
    ADD COLUMN result_json JSON NULL,
    ADD COLUMN warnings JSON NULL,
    ADD COLUMN data_as_of DATETIME(6) NULL,
    ADD CONSTRAINT fk_job_analysis_run FOREIGN KEY (analysis_run_id) REFERENCES portfolio_analysis_run (id);

ALTER TABLE job_attempt
    ADD COLUMN result_json JSON NULL,
    ADD COLUMN warnings JSON NULL,
    ADD COLUMN data_as_of DATETIME(6) NULL;

ALTER TABLE portfolio_analysis_run
    ADD COLUMN run_key VARCHAR(160) NULL;

UPDATE portfolio_analysis_run
SET run_key=CONCAT('import:', BIN_TO_UUID(import_batch_id))
WHERE run_key IS NULL AND import_batch_id IS NOT NULL;

ALTER TABLE portfolio_analysis_run
    MODIFY COLUMN run_key VARCHAR(160) NOT NULL,
    ADD UNIQUE KEY uq_analysis_run_key (run_key);

ALTER TABLE portfolio_drawdown_snapshot
    ADD COLUMN user_id BINARY(16) NULL;

UPDATE portfolio_drawdown_snapshot
SET user_id = (SELECT id FROM app_user ORDER BY created_at, id LIMIT 1)
WHERE user_id IS NULL;

ALTER TABLE portfolio_drawdown_snapshot
    MODIFY COLUMN user_id BINARY(16) NOT NULL,
    DROP INDEX uq_drawdown_evidence,
    ADD CONSTRAINT fk_drawdown_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    ADD KEY ix_drawdown_user_latest (user_id, data_as_of, created_at),
    ADD UNIQUE KEY uq_drawdown_user_evidence (user_id, strategy_version, data_as_of, evidence_checksum);
