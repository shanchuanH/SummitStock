CREATE TABLE instrument (
    id BINARY(16) NOT NULL,
    symbol VARCHAR(32) NOT NULL,
    exchange VARCHAR(32) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    currency CHAR(3) NOT NULL,
    cik VARCHAR(10) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_instrument_symbol_exchange (symbol, exchange),
    KEY ix_instrument_symbol_active (symbol, active),
    CONSTRAINT chk_instrument_asset_type CHECK (asset_type IN ('EQUITY', 'ETF', 'INDEX', 'MUTUAL_FUND', 'CASH'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE price_bar (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    timeframe VARCHAR(16) NOT NULL,
    bar_start DATETIME(6) NOT NULL,
    market_date DATE NOT NULL,
    open_price DECIMAL(24,8) NOT NULL,
    high_price DECIMAL(24,8) NOT NULL,
    low_price DECIMAL(24,8) NOT NULL,
    close_price DECIMAL(24,8) NOT NULL,
    volume DECIMAL(28,10) NOT NULL,
    adjusted BOOLEAN NOT NULL,
    provider VARCHAR(64) NOT NULL,
    source_timestamp DATETIME(6) NOT NULL,
    checksum CHAR(64) NOT NULL,
    normalization_version VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    raw_payload JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_price_bar_identity (instrument_id, timeframe, bar_start, adjusted, provider),
    KEY ix_price_bar_market_date (instrument_id, market_date, adjusted),
    CONSTRAINT fk_price_bar_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_price_bar_prices CHECK (
        open_price > 0 AND high_price > 0 AND low_price > 0 AND close_price > 0
        AND high_price >= open_price AND high_price >= close_price AND high_price >= low_price
        AND low_price <= open_price AND low_price <= close_price
    ),
    CONSTRAINT chk_price_bar_volume CHECK (volume >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE quote (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    bid_price DECIMAL(24,8) NULL,
    ask_price DECIMAL(24,8) NULL,
    last_price DECIMAL(24,8) NULL,
    currency CHAR(3) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    source_timestamp DATETIME(6) NOT NULL,
    checksum CHAR(64) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    raw_payload JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_quote_identity (instrument_id, provider, source_timestamp),
    KEY ix_quote_latest (instrument_id, data_as_of),
    CONSTRAINT fk_quote_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE corporate_action (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    ex_date DATE NOT NULL,
    effective_date DATE NULL,
    ratio_value DECIMAL(24,10) NULL,
    cash_amount DECIMAL(24,8) NULL,
    currency CHAR(3) NULL,
    provider VARCHAR(64) NOT NULL,
    source_timestamp DATETIME(6) NOT NULL,
    checksum CHAR(64) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    raw_payload JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_corporate_action_identity (instrument_id, action_type, ex_date, provider, checksum),
    CONSTRAINT fk_corporate_action_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE provider_request (
    id BINARY(16) NOT NULL,
    request_key CHAR(64) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL,
    http_status INT NULL,
    requested_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    source_timestamp DATETIME(6) NULL,
    response_checksum CHAR(64) NULL,
    normalization_version VARCHAR(32) NOT NULL,
    quality_status VARCHAR(32) NULL,
    error_code VARCHAR(64) NULL,
    error_detail VARCHAR(1024) NULL,
    request_context JSON NOT NULL,
    response_metadata JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_provider_request_key (request_key),
    KEY ix_provider_request_status (provider, status, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE data_quality_event (
    id BINARY(16) NOT NULL,
    event_key CHAR(64) NOT NULL,
    instrument_id BINARY(16) NULL,
    dataset VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    event_code VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    market_date DATE NULL,
    data_as_of DATETIME(6) NOT NULL,
    detail VARCHAR(1024) NOT NULL,
    context JSON NOT NULL,
    detected_at DATETIME(6) NOT NULL,
    resolved_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_data_quality_event_key (event_key),
    KEY ix_data_quality_open (status, severity, detected_at),
    CONSTRAINT fk_data_quality_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_data_quality_severity CHECK (severity IN ('INFO', 'WARNING', 'ERROR', 'CRITICAL')),
    CONSTRAINT chk_data_quality_status CHECK (status IN ('OPEN', 'RESOLVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE fundamental_observation (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    metric_code VARCHAR(128) NOT NULL,
    period_type VARCHAR(16) NOT NULL,
    period_start DATE NULL,
    period_end DATE NOT NULL,
    filing_date DATE NULL,
    value_decimal DECIMAL(28,10) NULL,
    value_text VARCHAR(2048) NULL,
    unit VARCHAR(32) NULL,
    currency CHAR(3) NULL,
    provider VARCHAR(64) NOT NULL,
    source_document VARCHAR(512) NULL,
    source_timestamp DATETIME(6) NOT NULL,
    checksum CHAR(64) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    raw_payload JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_fundamental_identity (
        instrument_id, metric_code, period_type, period_end, provider, checksum
    ),
    KEY ix_fundamental_latest (instrument_id, metric_code, period_end),
    CONSTRAINT fk_fundamental_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_fundamental_value CHECK (value_decimal IS NOT NULL OR value_text IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE company_event (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    event_at DATETIME(6) NOT NULL,
    market_date DATE NOT NULL,
    title VARCHAR(512) NOT NULL,
    source VARCHAR(64) NOT NULL,
    source_url VARCHAR(1024) NULL,
    checksum CHAR(64) NOT NULL,
    quality_status VARCHAR(32) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    metadata JSON NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_company_event_identity (instrument_id, event_type, event_at, source, checksum),
    KEY ix_company_event_market_date (instrument_id, market_date),
    CONSTRAINT fk_company_event_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE indicator_snapshot (
    id BINARY(16) NOT NULL,
    instrument_id BINARY(16) NOT NULL,
    market_date DATE NOT NULL,
    indicator_code VARCHAR(64) NOT NULL,
    parameters_hash CHAR(64) NOT NULL,
    adjusted BOOLEAN NOT NULL,
    status VARCHAR(32) NOT NULL,
    value_double DOUBLE NULL,
    values_json JSON NULL,
    required_observations INT NOT NULL,
    actual_observations INT NOT NULL,
    warnings JSON NOT NULL,
    source_bar_checksum CHAR(64) NOT NULL,
    normalization_version VARCHAR(32) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_indicator_snapshot_identity (
        instrument_id, market_date, indicator_code, parameters_hash, adjusted, source_bar_checksum
    ),
    KEY ix_indicator_snapshot_latest (instrument_id, indicator_code, market_date),
    CONSTRAINT fk_indicator_instrument FOREIGN KEY (instrument_id) REFERENCES instrument (id),
    CONSTRAINT chk_indicator_observations CHECK (
        required_observations >= 0 AND actual_observations >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO instrument (
    id, symbol, exchange, asset_type, currency, cik, active, metadata, created_at, updated_at, version
) VALUES
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000101'), 'SPY', 'ARCX', 'ETF', 'USD', NULL, TRUE, JSON_OBJECT('benchmark', TRUE), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
    (UUID_TO_BIN('00000000-0000-0000-0000-000000000102'), 'QQQ', 'XNAS', 'ETF', 'USD', NULL, TRUE, JSON_OBJECT('benchmark', TRUE), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
