ALTER TABLE recommendation
    ADD COLUMN risk_calculation_reason VARCHAR(96) NOT NULL DEFAULT 'LEGACY_NOT_CALCULATED'
        AFTER risk_after_fraction;
