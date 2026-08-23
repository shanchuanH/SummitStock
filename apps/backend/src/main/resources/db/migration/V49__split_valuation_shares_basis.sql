UPDATE financial_metric_snapshot
SET metric_code='DILUTED_WEIGHTED_AVG_SHARES'
WHERE metric_code='DILUTED_SHARES';

UPDATE financial_fact_observation
SET canonical_metric='DILUTED_WEIGHTED_AVG_SHARES'
WHERE canonical_metric='DILUTED_SHARES';
