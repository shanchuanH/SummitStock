package com.example.portfolio.market.persistence;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MarketDataStore {
    private final JdbcClient jdbc;
    private final JdbcTemplate batchJdbc;

    public MarketDataStore(JdbcClient jdbc, JdbcTemplate batchJdbc) {
        this.jdbc = jdbc;
        this.batchJdbc = batchJdbc;
    }

    @Transactional
    public int upsertBars(List<PriceBarWrite> bars) {
        if (bars.isEmpty()) return 0;
        var counts = batchJdbc.batchUpdate(
                """
                INSERT INTO price_bar (
                    id, instrument_id, timeframe, bar_start, market_date,
                    open_price, high_price, low_price, close_price, volume,
                    adjusted, provider, source_timestamp, checksum,
                    normalization_version, quality_status, data_as_of, raw_payload, created_at
                ) VALUES (
                    UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?
                ) AS incoming
                ON DUPLICATE KEY UPDATE
                    market_date = incoming.market_date,
                    open_price = incoming.open_price,
                    high_price = incoming.high_price,
                    low_price = incoming.low_price,
                    close_price = incoming.close_price,
                    volume = incoming.volume,
                    source_timestamp = incoming.source_timestamp,
                    checksum = incoming.checksum,
                    normalization_version = incoming.normalization_version,
                    quality_status = incoming.quality_status,
                    data_as_of = incoming.data_as_of,
                    raw_payload = incoming.raw_payload
                """,
                bars,
                500,
                (PreparedStatement statement, PriceBarWrite bar) -> {
                    int index = 1;
                    statement.setString(index++, bar.id().toString());
                    statement.setString(index++, bar.instrumentId().toString());
                    statement.setString(index++, bar.timeframe());
                    statement.setTimestamp(index++, Timestamp.from(bar.barStart()));
                    statement.setObject(index++, bar.marketDate());
                    statement.setBigDecimal(index++, bar.open());
                    statement.setBigDecimal(index++, bar.high());
                    statement.setBigDecimal(index++, bar.low());
                    statement.setBigDecimal(index++, bar.close());
                    statement.setBigDecimal(index++, bar.volume());
                    statement.setBoolean(index++, bar.adjusted());
                    statement.setString(index++, bar.provider());
                    statement.setTimestamp(index++, Timestamp.from(bar.sourceTimestamp()));
                    statement.setString(index++, bar.checksum());
                    statement.setString(index++, bar.normalizationVersion());
                    statement.setString(index++, bar.qualityStatus());
                    statement.setTimestamp(index++, Timestamp.from(bar.dataAsOf()));
                    statement.setString(index++, bar.rawPayload());
                    statement.setTimestamp(index, Timestamp.from(bar.createdAt()));
                });
        return java.util.Arrays.stream(counts)
                .flatMapToInt(java.util.Arrays::stream)
                .sum();
    }

    @Transactional
    public int appendIndicatorSnapshots(List<IndicatorSnapshotWrite> snapshots) {
        if (snapshots.isEmpty()) return 0;
        var counts = batchJdbc.batchUpdate(
                """
                INSERT IGNORE INTO indicator_snapshot (
                    id, instrument_id, market_date, indicator_code, parameters_hash,
                    adjusted, status, value_double, values_json, required_observations,
                    actual_observations, warnings, source_bar_checksum,
                    normalization_version, data_as_of, created_at
                ) VALUES (
                    UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?,
                    CAST(? AS JSON), ?, ?, ?, ?
                )
                """,
                snapshots,
                500,
                (PreparedStatement statement, IndicatorSnapshotWrite snapshot) -> {
                    int index = 1;
                    statement.setString(index++, snapshot.id().toString());
                    statement.setString(index++, snapshot.instrumentId().toString());
                    statement.setObject(index++, snapshot.marketDate());
                    statement.setString(index++, snapshot.indicatorCode());
                    statement.setString(index++, snapshot.parametersHash());
                    statement.setBoolean(index++, snapshot.adjusted());
                    statement.setString(index++, snapshot.status());
                    if (snapshot.value() == null) statement.setNull(index++, java.sql.Types.DOUBLE);
                    else statement.setDouble(index++, snapshot.value());
                    statement.setString(index++, snapshot.valuesJson());
                    statement.setInt(index++, snapshot.requiredObservations());
                    statement.setInt(index++, snapshot.actualObservations());
                    statement.setString(index++, snapshot.warningsJson());
                    statement.setString(index++, snapshot.sourceBarChecksum());
                    statement.setString(index++, snapshot.normalizationVersion());
                    statement.setTimestamp(index++, Timestamp.from(snapshot.dataAsOf()));
                    statement.setTimestamp(index, Timestamp.from(snapshot.createdAt()));
                });
        return java.util.Arrays.stream(counts)
                .flatMapToInt(java.util.Arrays::stream)
                .sum();
    }

    public List<InstrumentView> instruments(String afterSymbol, int limit) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) id, symbol, exchange, asset_type, currency, cik, active
                        FROM instrument
                        WHERE active = TRUE AND symbol > :afterSymbol
                        ORDER BY symbol
                        LIMIT :limit
                        """)
                .param("afterSymbol", afterSymbol)
                .param("limit", limit)
                .query(InstrumentView.class)
                .list();
    }

    public List<PriceBarView> bars(String symbol, boolean adjusted, int limit) {
        return jdbc.sql(
                        """
                        SELECT p.market_date, p.open_price, p.high_price, p.low_price,
                               p.close_price, p.volume, p.adjusted, p.provider,
                               p.quality_status, p.data_as_of
                        FROM price_bar p
                        JOIN instrument i ON i.id = p.instrument_id
                        WHERE i.symbol = :symbol AND p.adjusted = :adjusted
                        ORDER BY p.market_date DESC
                        LIMIT :limit
                        """)
                .param("symbol", symbol.toUpperCase(java.util.Locale.ROOT))
                .param("adjusted", adjusted)
                .param("limit", limit)
                .query(PriceBarView.class)
                .list();
    }

    public List<IndicatorView> indicators(String symbol, int limit) {
        return jdbc.sql(
                        """
                        SELECT s.market_date, s.indicator_code, s.status, s.value_double,
                               s.values_json, s.required_observations, s.actual_observations,
                               s.data_as_of
                        FROM indicator_snapshot s
                        JOIN instrument i ON i.id = s.instrument_id
                        WHERE i.symbol = :symbol
                        ORDER BY s.market_date DESC, s.indicator_code
                        LIMIT :limit
                        """)
                .param("symbol", symbol.toUpperCase(java.util.Locale.ROOT))
                .param("limit", limit)
                .query(IndicatorView.class)
                .list();
    }

    public List<FundamentalView> fundamentals(String symbol, int limit) {
        return jdbc.sql(
                        """
                        SELECT f.metric_code, f.period_type, f.period_end, f.filing_date,
                               f.value_decimal, f.value_text, f.unit, f.currency,
                               f.provider, f.quality_status, f.data_as_of
                        FROM fundamental_observation f
                        JOIN instrument i ON i.id = f.instrument_id
                        WHERE i.symbol = :symbol
                        ORDER BY f.period_end DESC, f.metric_code
                        LIMIT :limit
                        """)
                .param("symbol", symbol.toUpperCase(java.util.Locale.ROOT))
                .param("limit", limit)
                .query(FundamentalView.class)
                .list();
    }

    public DataHealthView dataHealth() {
        return jdbc.sql(
                        """
                        SELECT
                            (SELECT COUNT(*) FROM instrument WHERE active = TRUE) active_instruments,
                            (SELECT COUNT(*) FROM price_bar) price_bars,
                            (SELECT COUNT(*) FROM indicator_snapshot) indicator_snapshots,
                            (SELECT COUNT(*) FROM data_quality_event WHERE status = 'OPEN') open_quality_events,
                            ((SELECT COUNT(*) FROM price_bar WHERE quality_status = 'STALE') +
                             (SELECT COUNT(*) FROM fundamental_observation WHERE quality_status = 'STALE')) stale_observations,
                            ((SELECT COUNT(*) FROM price_bar WHERE quality_status = 'PARTIAL') +
                             (SELECT COUNT(*) FROM fundamental_observation WHERE quality_status = 'PARTIAL')) partial_observations,
                            ((SELECT COUNT(*) FROM price_bar WHERE quality_status = 'SUSPECT') +
                             (SELECT COUNT(*) FROM fundamental_observation WHERE quality_status = 'SUSPECT')) suspect_observations,
                            (SELECT MAX(data_as_of) FROM price_bar) latest_price_data_as_of,
                            (SELECT MAX(data_as_of) FROM indicator_snapshot) latest_indicator_data_as_of
                        """)
                .query(DataHealthView.class)
                .single();
    }

    public record PriceBarWrite(
            UUID id,
            UUID instrumentId,
            String timeframe,
            Instant barStart,
            LocalDate marketDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            boolean adjusted,
            String provider,
            Instant sourceTimestamp,
            String checksum,
            String normalizationVersion,
            String qualityStatus,
            Instant dataAsOf,
            String rawPayload,
            Instant createdAt) {}

    public record IndicatorSnapshotWrite(
            UUID id,
            UUID instrumentId,
            LocalDate marketDate,
            String indicatorCode,
            String parametersHash,
            boolean adjusted,
            String status,
            Double value,
            String valuesJson,
            int requiredObservations,
            int actualObservations,
            String warningsJson,
            String sourceBarChecksum,
            String normalizationVersion,
            Instant dataAsOf,
            Instant createdAt) {}

    public record InstrumentView(
            UUID id, String symbol, String exchange, String assetType, String currency, String cik, boolean active) {}

    public record PriceBarView(
            LocalDate marketDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal volume,
            boolean adjusted,
            String provider,
            String qualityStatus,
            LocalDateTime dataAsOf) {}

    public record IndicatorView(
            LocalDate marketDate,
            String indicatorCode,
            String status,
            Double valueDouble,
            String valuesJson,
            int requiredObservations,
            int actualObservations,
            LocalDateTime dataAsOf) {}

    public record FundamentalView(
            String metricCode,
            String periodType,
            LocalDate periodEnd,
            LocalDate filingDate,
            BigDecimal valueDecimal,
            String valueText,
            String unit,
            String currency,
            String provider,
            String qualityStatus,
            LocalDateTime dataAsOf) {}

    public record DataHealthView(
            long activeInstruments,
            long priceBars,
            long indicatorSnapshots,
            long openQualityEvents,
            long staleObservations,
            long partialObservations,
            long suspectObservations,
            LocalDateTime latestPriceDataAsOf,
            LocalDateTime latestIndicatorDataAsOf) {}
}
