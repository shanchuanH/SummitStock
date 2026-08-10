package com.example.portfolio.market.web;

import com.example.portfolio.market.persistence.MarketDataStore;
import com.example.portfolio.market.persistence.MarketDataStore.DataHealthView;
import com.example.portfolio.market.persistence.MarketDataStore.FundamentalView;
import com.example.portfolio.market.persistence.MarketDataStore.IndicatorView;
import com.example.portfolio.market.persistence.MarketDataStore.InstrumentView;
import com.example.portfolio.market.persistence.MarketDataStore.PriceBarView;
import com.example.portfolio.market.provider.ProviderRuntimeStatus;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MarketDataController {
    private final MarketDataStore store;
    private final Clock clock;
    private final ObjectProvider<ProviderRuntimeStatus> providerRuntimeStatus;

    public MarketDataController(
            MarketDataStore store, Clock clock, ObjectProvider<ProviderRuntimeStatus> providerRuntimeStatus) {
        this.store = store;
        this.clock = clock;
        this.providerRuntimeStatus = providerRuntimeStatus;
    }

    @GetMapping("/instruments")
    InstrumentPage instruments(
            @RequestParam(defaultValue = "") String cursor, @RequestParam(defaultValue = "50") int limit) {
        int safeLimit = Math.clamp(limit, 1, 200);
        var items = store.instruments(cursor.toUpperCase(java.util.Locale.ROOT), safeLimit);
        var nextCursor = items.size() == safeLimit ? items.getLast().symbol() : null;
        return new InstrumentPage(items.stream().map(InstrumentResponse::from).toList(), nextCursor, clock.instant());
    }

    @GetMapping("/instruments/{symbol}/bars")
    MarketSeries<PriceBarResponse> bars(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "true") boolean adjusted,
            @RequestParam(defaultValue = "252") int limit) {
        var rows = store.bars(symbol, adjusted, Math.clamp(limit, 1, 1000));
        return new MarketSeries<>(rows.stream().map(PriceBarResponse::from).toList(), latestBar(rows));
    }

    @GetMapping("/instruments/{symbol}/indicators")
    MarketSeries<IndicatorResponse> indicators(
            @PathVariable String symbol, @RequestParam(defaultValue = "200") int limit) {
        var rows = store.indicators(symbol, Math.clamp(limit, 1, 1000));
        return new MarketSeries<>(rows.stream().map(IndicatorResponse::from).toList(), latestIndicator(rows));
    }

    @GetMapping("/instruments/{symbol}/fundamentals")
    MarketSeries<FundamentalResponse> fundamentals(
            @PathVariable String symbol, @RequestParam(defaultValue = "100") int limit) {
        var rows = store.fundamentals(symbol, Math.clamp(limit, 1, 500));
        return new MarketSeries<>(rows.stream().map(FundamentalResponse::from).toList(), latestFundamental(rows));
    }

    @GetMapping("/market/data-health")
    DataHealthResponse dataHealth() {
        return DataHealthResponse.from(
                store.dataHealth(),
                clock.instant(),
                providerRuntimeStatus.getIfAvailable(ProviderRuntimeStatus::complete));
    }

    private static Instant latestBar(List<PriceBarView> rows) {
        return utc(rows.stream()
                .map(PriceBarView::dataAsOf)
                .max(LocalDateTime::compareTo)
                .orElse(null));
    }

    private static Instant latestIndicator(List<IndicatorView> rows) {
        return rows.stream()
                .map(IndicatorView::dataAsOf)
                .max(LocalDateTime::compareTo)
                .map(value -> value.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    private static Instant latestFundamental(List<FundamentalView> rows) {
        return rows.stream()
                .map(FundamentalView::dataAsOf)
                .max(LocalDateTime::compareTo)
                .map(value -> value.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    public record InstrumentPage(List<InstrumentResponse> items, String nextCursor, Instant dataAsOf) {}

    public record InstrumentResponse(
            String id, String symbol, String exchange, String assetType, String currency, String cik, boolean active) {
        static InstrumentResponse from(InstrumentView row) {
            return new InstrumentResponse(
                    row.id().toString(),
                    row.symbol(),
                    row.exchange(),
                    row.assetType(),
                    row.currency(),
                    row.cik(),
                    row.active());
        }
    }

    public record MarketSeries<T>(List<T> items, Instant dataAsOf) {
        public MarketSeries {
            items = List.copyOf(items);
        }
    }

    public record PriceBarResponse(
            LocalDate marketDate,
            String open,
            String high,
            String low,
            String close,
            String volume,
            boolean adjusted,
            String provider,
            String qualityStatus,
            Instant dataAsOf) {
        static PriceBarResponse from(PriceBarView row) {
            return new PriceBarResponse(
                    row.marketDate(),
                    decimal(row.openPrice()),
                    decimal(row.highPrice()),
                    decimal(row.lowPrice()),
                    decimal(row.closePrice()),
                    decimal(row.volume()),
                    row.adjusted(),
                    row.provider(),
                    row.qualityStatus(),
                    utc(row.dataAsOf()));
        }
    }

    public record IndicatorResponse(
            LocalDate marketDate,
            String indicatorCode,
            String status,
            Double value,
            String valuesJson,
            int requiredObservations,
            int actualObservations,
            Instant dataAsOf) {
        static IndicatorResponse from(IndicatorView row) {
            return new IndicatorResponse(
                    row.marketDate(),
                    row.indicatorCode(),
                    row.status(),
                    row.valueDouble(),
                    row.valuesJson(),
                    row.requiredObservations(),
                    row.actualObservations(),
                    utc(row.dataAsOf()));
        }
    }

    public record FundamentalResponse(
            String metricCode,
            String periodType,
            LocalDate periodEnd,
            LocalDate filingDate,
            String value,
            String text,
            String unit,
            String currency,
            String provider,
            String qualityStatus,
            Instant dataAsOf) {
        static FundamentalResponse from(FundamentalView row) {
            return new FundamentalResponse(
                    row.metricCode(),
                    row.periodType(),
                    row.periodEnd(),
                    row.filingDate(),
                    decimal(row.valueDecimal()),
                    row.valueText(),
                    row.unit(),
                    row.currency(),
                    row.provider(),
                    row.qualityStatus(),
                    utc(row.dataAsOf()));
        }
    }

    public record DataHealthResponse(
            String status,
            long activeInstruments,
            long priceBars,
            long indicatorSnapshots,
            long openQualityEvents,
            long staleObservations,
            long partialObservations,
            long suspectObservations,
            Instant latestPriceDataAsOf,
            Instant latestIndicatorDataAsOf,
            Instant dataAsOf) {
        static DataHealthResponse from(DataHealthView row, Instant now, ProviderRuntimeStatus providerStatus) {
            String status;
            if ("PARTIAL".equals(providerStatus.status())) status = "PARTIAL";
            else if (row.priceBars() == 0) status = "EMPTY";
            else if (row.openQualityEvents() > 0 || row.suspectObservations() > 0) status = "DEGRADED";
            else if (row.latestPriceDataAsOf() == null
                    || utc(row.latestPriceDataAsOf()).isBefore(now.minus(3, ChronoUnit.DAYS))) status = "STALE";
            else status = "HEALTHY";
            return new DataHealthResponse(
                    status,
                    row.activeInstruments(),
                    row.priceBars(),
                    row.indicatorSnapshots(),
                    row.openQualityEvents(),
                    row.staleObservations(),
                    row.partialObservations(),
                    row.suspectObservations(),
                    utc(row.latestPriceDataAsOf()),
                    utc(row.latestIndicatorDataAsOf()),
                    now);
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static Instant utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
