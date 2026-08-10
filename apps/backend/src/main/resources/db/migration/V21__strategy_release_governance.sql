ALTER TABLE strategy_version
    ADD COLUMN approved_backtest_run_id BINARY(16) NULL,
    ADD COLUMN backtest_artifact_checksum CHAR(64) NULL,
    ADD COLUMN approved_by VARCHAR(320) NULL,
    ADD COLUMN approved_at DATETIME(6) NULL,
    ADD CONSTRAINT fk_strategy_approved_backtest
        FOREIGN KEY (approved_backtest_run_id) REFERENCES backtest_run (id),
    ADD CONSTRAINT chk_strategy_release_evidence CHECK (
        (approved_at IS NULL AND approved_backtest_run_id IS NULL AND backtest_artifact_checksum IS NULL AND approved_by IS NULL)
        OR
        (approved_at IS NOT NULL AND approved_backtest_run_id IS NOT NULL AND backtest_artifact_checksum IS NOT NULL AND approved_by IS NOT NULL)
    );
