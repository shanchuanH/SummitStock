ALTER TABLE recommendation
    ADD COLUMN tax_lot_status VARCHAR(48) NOT NULL DEFAULT 'LEGACY_UNKNOWN'
        AFTER risk_calculation_reason;
