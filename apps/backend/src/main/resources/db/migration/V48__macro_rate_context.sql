ALTER TABLE macro_factor_snapshot
    ADD COLUMN ten_year_yield DECIMAL(18,8) NULL,
    ADD COLUMN two_year_yield DECIMAL(18,8) NULL,
    ADD COLUMN fed_funds_rate DECIMAL(18,8) NULL,
    ADD COLUMN ten_year_real_yield DECIMAL(18,8) NULL;
