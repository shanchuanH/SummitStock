ALTER TABLE etf_dip_event
    ADD COLUMN config_hash CHAR(64) NULL AFTER strategy_version,
    ADD COLUMN instrument_drawdown DECIMAL(18,10) NULL AFTER portfolio_drawdown,
    ADD COLUMN trigger_codes JSON NULL AFTER trigger_count,
    ADD COLUMN tranche_index INT NULL AFTER trigger_codes,
    ADD COLUMN tranche_pct DECIMAL(18,10) NULL AFTER tranche_index,
    ADD COLUMN last_tranche_market_date DATE NULL AFTER tranche_pct,
    ADD COLUMN cooldown_until_market_date DATE NULL AFTER last_tranche_market_date,
    ADD COLUMN reserve_before DECIMAL(24,8) NULL AFTER cooldown_until_market_date,
    ADD COLUMN reserve_after DECIMAL(24,8) NULL AFTER reserve_before,
    ADD COLUMN quality VARCHAR(32) NOT NULL DEFAULT 'MISSING' AFTER reserve_after;

ALTER TABLE etf_dip_event
    ADD CONSTRAINT chk_etf_dip_tranche_index CHECK (tranche_index IS NULL OR tranche_index BETWEEN 1 AND 4),
    ADD CONSTRAINT chk_etf_dip_tranche_pct CHECK (tranche_pct IS NULL OR (tranche_pct > 0 AND tranche_pct <= 0.30)),
    ADD CONSTRAINT chk_etf_dip_reserve CHECK (
        (reserve_before IS NULL AND reserve_after IS NULL)
        OR (reserve_before >= 0 AND reserve_after >= 0 AND reserve_after <= reserve_before)
    );

UPDATE etf_dip_event
SET config_hash = REPEAT('0', 64), trigger_codes = JSON_ARRAY(), quality = 'MISSING'
WHERE config_hash IS NULL OR trigger_codes IS NULL;

ALTER TABLE etf_dip_event
    MODIFY config_hash CHAR(64) NOT NULL,
    MODIFY trigger_codes JSON NOT NULL;
