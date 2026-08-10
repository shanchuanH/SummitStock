ALTER TABLE financial_fact_observation
    ADD COLUMN provider_fiscal_year INT NULL AFTER source,
    ADD COLUMN provider_fiscal_period VARCHAR(8) NULL AFTER provider_fiscal_year,
    ADD CONSTRAINT chk_financial_fact_fiscal_period
        CHECK (provider_fiscal_period IS NULL OR provider_fiscal_period IN ('FY','Q1','Q2','Q3','Q4'));

CREATE INDEX ix_financial_period_fiscal_comparable
    ON financial_period (instrument_id, period_type, fiscal_year, fiscal_quarter, filed_at);
