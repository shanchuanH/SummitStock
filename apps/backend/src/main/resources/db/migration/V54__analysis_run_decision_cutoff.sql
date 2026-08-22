ALTER TABLE portfolio_analysis_run
    ADD COLUMN decision_cutoff DATETIME(6) NULL AFTER market_date,
    ADD KEY ix_analysis_run_decision_cutoff (user_id,market_date,decision_cutoff);

-- Existing runs intentionally remain NULL. Their historical cutoff cannot be reconstructed safely,
-- so replay/report code fails closed instead of substituting a UTC end-of-day timestamp.
