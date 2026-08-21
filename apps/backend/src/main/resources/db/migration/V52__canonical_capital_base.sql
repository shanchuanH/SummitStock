ALTER TABLE portfolio_capital_snapshot
    ADD COLUMN required_emergency_floor DECIMAL(24,8) NOT NULL DEFAULT 0 AFTER tracked_cash;
