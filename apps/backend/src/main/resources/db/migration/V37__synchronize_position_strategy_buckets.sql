UPDATE position
SET bucket = CASE
    WHEN classification IN ('CORE_BROAD_ETF', 'CORE_TECH_ETF', 'CASH_EQUIVALENT') THEN 'CORE'
    ELSE 'TACTICAL_OVERLAY'
END
WHERE classification_confirmed = TRUE;
