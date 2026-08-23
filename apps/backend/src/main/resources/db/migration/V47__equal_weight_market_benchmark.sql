INSERT IGNORE INTO instrument
    (id,symbol,exchange,asset_type,currency,active,metadata,created_at,updated_at,version)
VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000103'),'RSP','ARCX','ETF','USD',TRUE,
     JSON_OBJECT('benchmark',TRUE,'role','SP500_EQUAL_WEIGHT'),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0);
