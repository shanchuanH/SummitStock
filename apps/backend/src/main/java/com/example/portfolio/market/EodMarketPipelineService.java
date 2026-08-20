package com.example.portfolio.market;

import com.example.portfolio.market.persistence.MarketDataStore;
import com.example.portfolio.market.persistence.MarketDataStore.IndicatorSnapshotWrite;
import com.example.portfolio.market.persistence.MarketDataStore.PriceBarWrite;
import com.example.portfolio.market.provider.MarketDataProvider;
import com.example.portfolio.market.provider.ProviderCallException;
import com.example.portfolio.market.provider.TradingCalendar;
import com.example.portfolio.quant.IndicatorResult;
import com.example.portfolio.quant.Indicators;
import com.example.portfolio.quant.QuantBar;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class EodMarketPipelineService {
    private final JdbcClient jdbc;
    private final MarketDataStore store;
    private final MarketDataProvider provider;
    private final TradingCalendar calendar;
    private final Clock clock;

    public EodMarketPipelineService(
            JdbcClient jdbc,
            MarketDataStore store,
            MarketDataProvider provider,
            TradingCalendar calendar,
            Clock clock) {
        this.jdbc = jdbc;
        this.store = store;
        this.provider = provider;
        this.calendar = calendar;
        this.clock = clock;
    }

    public StageCount collectBars(LocalDate marketDate) {
        var completedSession = marketDate.isBefore(calendar.latestCompletedSession(clock.instant()))
                ? marketDate
                : calendar.latestCompletedSession(clock.instant());
        int observations = 0;
        int affected = 0;
        var failed = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var instrument : trackedInstruments()) {
            var latest = jdbc.sql("SELECT MAX(market_date) FROM price_bar WHERE instrument_id=UUID_TO_BIN(:id) "
                            + "AND adjusted=TRUE AND market_date<=:completedSession")
                    .param("id", instrument.id().toString())
                    .param("completedSession", completedSession)
                    .query(LocalDate.class)
                    .optional()
                    .orElse(completedSession.minusYears(5));
            var from = latest.isBefore(completedSession) ? latest.plusDays(1) : completedSession;
            var attempt = providerAttempt(
                    () -> provider.fetchDailyBars(instrument.symbol(), from, completedSession),
                    instrument.symbol(),
                    failed,
                    warnings);
            if (attempt.isEmpty()) continue;
            var result = attempt.orElseThrow();
            var now = clock.instant();
            var writes = result.bars().stream()
                    .map(bar -> new PriceBarWrite(
                            UUID.randomUUID(),
                            instrument.id(),
                            "1D",
                            bar.marketDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                            bar.marketDate(),
                            bar.open(),
                            bar.high(),
                            bar.low(),
                            bar.close(),
                            bar.volume(),
                            bar.adjusted(),
                            result.provenance().provider(),
                            result.provenance().sourceTimestamp(),
                            sha256(result.provenance().checksum() + ":" + bar.marketDate()),
                            result.provenance().normalizationVersion(),
                            result.provenance().qualityStatus().name(),
                            result.provenance().fetchedAt(),
                            "{}",
                            now))
                    .toList();
            observations += writes.size();
            affected += store.upsertBars(writes);
        }
        enforceBenchmarkAvailability(failed);
        return new StageCount(observations, affected, failed, warnings);
    }

    public StageCount collectQuotes() {
        int observations = 0;
        var failed = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var instrument : trackedInstruments()) {
            var attempt = providerAttempt(
                    () -> provider.fetchQuote(instrument.symbol()), instrument.symbol(), failed, warnings);
            if (attempt.isEmpty()) continue;
            var value = attempt.orElseThrow();
            jdbc.sql(
                            """
                            INSERT IGNORE INTO quote (
                                id, instrument_id, bid_price, ask_price, last_price, decision_market_date,
                                currency, provider, source_timestamp, checksum, quality_status,
                                decision_quality_status, execution_quality_status, spread_fraction,
                                data_as_of, raw_payload, created_at
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), :bid, :ask, :last, :marketDate,
                                :currency, :provider, :sourceTimestamp, :checksum, :quality,
                                :decisionQuality, :executionQuality, :spread,
                                :dataAsOf, JSON_OBJECT(), :now
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("instrumentId", instrument.id().toString())
                    .param("bid", value.bid())
                    .param("ask", value.ask())
                    .param("last", value.last())
                    .param("marketDate", value.decisionPrice().marketDate())
                    .param("currency", value.currency())
                    .param("provider", value.provenance().provider())
                    .param("sourceTimestamp", value.provenance().sourceTimestamp())
                    .param("checksum", value.provenance().checksum())
                    .param("quality", value.decisionPrice().quality().name())
                    .param("decisionQuality", value.decisionPrice().quality().name())
                    .param(
                            "executionQuality",
                            value.executionLiquidity().quality().name())
                    .param("spread", value.executionLiquidity().spreadFraction())
                    .param("dataAsOf", value.provenance().fetchedAt())
                    .param("now", clock.instant())
                    .update();
            observations++;
        }
        enforceBenchmarkAvailability(failed);
        return new StageCount(observations, observations, failed, warnings);
    }

    public StageCount collectCorporateActions(LocalDate marketDate) {
        int observations = 0;
        int affected = 0;
        var failed = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var instrument : trackedInstruments()) {
            var attempt = providerAttempt(
                    () -> provider.fetchCorporateActions(instrument.symbol(), marketDate.minusYears(1), marketDate),
                    instrument.symbol(),
                    failed,
                    warnings);
            if (attempt.isEmpty()) continue;
            var result = attempt.orElseThrow();
            for (var action : result.actions()) {
                observations++;
                affected += jdbc.sql(
                                """
                                INSERT IGNORE INTO corporate_action (
                                    id, instrument_id, action_type, ex_date, effective_date, ratio_value,
                                    cash_amount, currency, provider, source_timestamp, checksum,
                                    quality_status, raw_payload, created_at
                                ) VALUES (
                                    UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), :type, :exDate, :effectiveDate,
                                    :ratio, :cash, :currency, :provider, :sourceTimestamp, :checksum,
                                    :quality, JSON_OBJECT(), :now
                                )
                                """)
                        .param("id", UUID.randomUUID().toString())
                        .param("instrumentId", instrument.id().toString())
                        .param("type", action.type())
                        .param("exDate", action.exDate())
                        .param("effectiveDate", action.effectiveDate())
                        .param("ratio", action.ratio())
                        .param("cash", action.cashAmount())
                        .param("currency", action.currency())
                        .param("provider", result.provenance().provider())
                        .param("sourceTimestamp", result.provenance().sourceTimestamp())
                        .param("checksum", sha256(result.provenance().checksum() + action))
                        .param("quality", result.provenance().qualityStatus().name())
                        .param("now", clock.instant())
                        .update();
            }
        }
        return new StageCount(observations, affected, failed, warnings);
    }

    public StageCount validateBars(LocalDate marketDate) {
        var invalid = jdbc.sql(
                        """
                        SELECT COUNT(*) FROM price_bar
                        WHERE market_date<=:marketDate AND (open_price<=0 OR high_price<low_price OR volume<0)
                        """)
                .param("marketDate", marketDate)
                .query(Long.class)
                .single();
        if (invalid > 0) throw new IllegalStateException("BAR_VALIDATION_FAILED");
        long available = jdbc.sql("SELECT COUNT(*) FROM price_bar WHERE market_date<=:marketDate")
                .param("marketDate", marketDate)
                .query(Long.class)
                .single();
        return new StageCount(Math.toIntExact(available), 0);
    }

    public StageCount computeIndicators(LocalDate marketDate) {
        int observations = 0;
        int affected = 0;
        for (var instrument : trackedInstruments()) {
            var rows = jdbc.sql(
                            """
                            SELECT market_date, open_price, high_price, low_price, close_price, volume, checksum
                            FROM price_bar WHERE instrument_id=UUID_TO_BIN(:id) AND adjusted=TRUE
                              AND market_date<=:marketDate ORDER BY market_date DESC LIMIT 500
                            """)
                    .param("id", instrument.id().toString())
                    .param("marketDate", marketDate)
                    .query(BarRow.class)
                    .list();
            if (rows.isEmpty()) continue;
            Collections.reverse(rows);
            var bars = rows.stream()
                    .map(row -> new QuantBar(
                            row.marketDate(),
                            row.openPrice(),
                            row.highPrice(),
                            row.lowPrice(),
                            row.closePrice(),
                            row.volume(),
                            true))
                    .toList();
            var date = rows.getLast().marketDate();
            var checksum = rows.getLast().checksum();
            var now = clock.instant();
            var writes = List.of(
                    snapshot(instrument.id(), date, "SMA_20", "period=20", Indicators.sma(bars, 20), checksum, now),
                    snapshot(instrument.id(), date, "SMA_50", "period=50", Indicators.sma(bars, 50), checksum, now),
                    snapshot(instrument.id(), date, "SMA_200", "period=200", Indicators.sma(bars, 200), checksum, now),
                    snapshot(instrument.id(), date, "EMA_20", "period=20", Indicators.ema(bars, 20), checksum, now),
                    snapshot(
                            instrument.id(),
                            date,
                            "ATR_14",
                            "period=14",
                            Indicators.wilderAtr(bars, 14),
                            checksum,
                            now),
                    snapshot(instrument.id(), date, "RSI_14", "period=14", Indicators.rsi(bars, 14), checksum, now),
                    macdSnapshot(instrument.id(), date, bars, checksum, now),
                    snapshot(
                            instrument.id(),
                            date,
                            "ROLLING_HIGH_63",
                            "period=63",
                            Indicators.rollingHigh(bars, 63),
                            checksum,
                            now),
                    snapshot(
                            instrument.id(),
                            date,
                            "REALIZED_VOL_20",
                            "period=20",
                            Indicators.realizedVolatility(bars, 20),
                            checksum,
                            now),
                    snapshot(
                            instrument.id(),
                            date,
                            "REALIZED_VOL_60",
                            "period=60",
                            Indicators.realizedVolatility(bars, 60),
                            checksum,
                            now));
            observations += writes.size();
            affected += store.appendIndicatorSnapshots(writes);
        }
        return new StageCount(observations, affected);
    }

    private List<TrackedInstrument> trackedInstruments() {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) id, symbol FROM instrument
                        WHERE active=TRUE AND asset_type IN ('EQUITY','ETF') AND (
                          JSON_EXTRACT(metadata, '$.benchmark') = TRUE
                          OR EXISTS (SELECT 1 FROM position p WHERE p.instrument_id=instrument.id AND p.status='OPEN')
                          OR EXISTS (SELECT 1 FROM investment_idea idea
                                     WHERE idea.instrument_id=instrument.id AND idea.source_type='WATCHLIST'
                                       AND idea.active=TRUE))
                        ORDER BY symbol
                        """)
                .query(TrackedInstrument.class)
                .list();
    }

    private static <T> Optional<T> providerAttempt(
            Supplier<T> call, String instrument, List<String> failed, List<String> warnings) {
        try {
            return Optional.of(call.get());
        } catch (ProviderCallException exception) {
            failed.add(instrument);
            warnings.add(instrument + ":" + exception.code());
            return Optional.empty();
        }
    }

    private static void enforceBenchmarkAvailability(List<String> failed) {
        if (failed.contains("SPY") && failed.contains("QQQ")) {
            throw new MarketPipelineException(
                    "BENCHMARK_DATASET_UNAVAILABLE", true, new IllegalStateException("SPY and QQQ both failed"));
        }
    }

    private static IndicatorSnapshotWrite snapshot(
            UUID id,
            LocalDate date,
            String code,
            String parameters,
            IndicatorResult<Double> result,
            String checksum,
            Instant now) {
        return new IndicatorSnapshotWrite(
                UUID.randomUUID(),
                id,
                date,
                code,
                sha256(parameters),
                true,
                result.status().name(),
                result.value().orElse(null),
                null,
                result.requiredObservations(),
                result.actualObservations(),
                jsonArray(result.warnings()),
                checksum,
                "pipeline-v1",
                now,
                now);
    }

    private static IndicatorSnapshotWrite macdSnapshot(
            UUID id, LocalDate date, List<QuantBar> bars, String checksum, Instant now) {
        var result = Indicators.macd(bars, 12, 26, 9);
        var value = result.value().map(Indicators.Macd::histogram).orElse(null);
        var json = result.value()
                .map(macd -> "{\"line\":" + macd.line() + ",\"signal\":" + macd.signal() + ",\"histogram\":"
                        + macd.histogram() + "}")
                .orElse(null);
        return new IndicatorSnapshotWrite(
                UUID.randomUUID(),
                id,
                date,
                "MACD_12_26_9",
                sha256("fast=12,slow=26,signal=9"),
                true,
                result.status().name(),
                value,
                json,
                result.requiredObservations(),
                result.actualObservations(),
                jsonArray(result.warnings()),
                checksum,
                "pipeline-v2",
                now,
                now);
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record StageCount(int observations, int affected, List<String> failedInstruments, List<String> warnings) {
        public StageCount {
            failedInstruments = List.copyOf(failedInstruments);
            warnings = List.copyOf(warnings);
        }

        public StageCount(int observations, int affected) {
            this(observations, affected, List.of(), List.of());
        }

        public String status() {
            if (failedInstruments.isEmpty()) return "SUCCESS";
            return observations == 0 ? "FAILED" : "PARTIAL";
        }
    }

    record TrackedInstrument(UUID id, String symbol) {}

    record BarRow(
            LocalDate marketDate,
            BigDecimal openPrice,
            BigDecimal highPrice,
            BigDecimal lowPrice,
            BigDecimal closePrice,
            BigDecimal volume,
            String checksum) {}
}
