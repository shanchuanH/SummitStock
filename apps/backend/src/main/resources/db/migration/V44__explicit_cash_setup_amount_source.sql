ALTER TABLE portfolio_cash_setup
    ADD COLUMN external_amount_source VARCHAR(32) NULL AFTER external_emergency_amount;

UPDATE portfolio_cash_setup
SET external_amount_source = 'LEGACY_INFERRED'
WHERE external_emergency_amount > 0;

ALTER TABLE portfolio_cash_setup
    ADD CONSTRAINT chk_portfolio_cash_setup_external_source CHECK (
        (external_emergency_amount = 0 AND external_amount_source IS NULL)
        OR
        (external_emergency_amount > 0
            AND external_amount_source IN ('USER_CONFIRMED_EXTERNAL', 'LEGACY_INFERRED'))
    );
