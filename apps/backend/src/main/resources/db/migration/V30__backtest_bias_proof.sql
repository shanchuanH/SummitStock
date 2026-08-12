ALTER TABLE backtest_run
    ADD COLUMN universe_version VARCHAR(128) NULL AFTER universe_checksum,
    ADD COLUMN price_adjustment_version VARCHAR(128) NULL AFTER universe_version,
    ADD COLUMN calendar_version VARCHAR(128) NULL AFTER price_adjustment_version,
    ADD COLUMN cost_model_version VARCHAR(128) NULL AFTER calendar_version,
    ADD COLUMN feature_cutoff_policy VARCHAR(128) NULL AFTER cost_model_version,
    ADD COLUMN bias_proof JSON NULL AFTER bias_status;

UPDATE backtest_run
SET bias_status='NOT_EVALUATED',
    universe_version='LEGACY_UNVERIFIED',
    price_adjustment_version='LEGACY_UNVERIFIED',
    calendar_version='LEGACY_UNVERIFIED',
    cost_model_version='LEGACY_UNVERIFIED',
    feature_cutoff_policy='LEGACY_UNVERIFIED',
    bias_proof=JSON_OBJECT('verified',FALSE,'failures',JSON_ARRAY('LEGACY_CALLER_SUPPLIED_STATUS'));

ALTER TABLE backtest_run
    MODIFY universe_version VARCHAR(128) NOT NULL,
    MODIFY price_adjustment_version VARCHAR(128) NOT NULL,
    MODIFY calendar_version VARCHAR(128) NOT NULL,
    MODIFY cost_model_version VARCHAR(128) NOT NULL,
    MODIFY feature_cutoff_policy VARCHAR(128) NOT NULL,
    MODIFY bias_proof JSON NOT NULL;
