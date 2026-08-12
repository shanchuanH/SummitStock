INSERT IGNORE INTO instrument (
    id, symbol, exchange, asset_type, currency, cik, active, metadata, created_at, updated_at, version
) VALUES (
    UUID_TO_BIN('00000000-0000-0000-0000-000000000216'), 'AMZN', 'XNAS', 'EQUITY', 'USD',
    '0001018724', TRUE, JSON_OBJECT(), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
);

INSERT IGNORE INTO instrument_alias (alias_symbol, instrument_id, source, created_at)
SELECT 'AMZN', id, 'SYSTEM_SEED', UTC_TIMESTAMP(6)
FROM instrument
WHERE symbol='AMZN' AND exchange='XNAS';
