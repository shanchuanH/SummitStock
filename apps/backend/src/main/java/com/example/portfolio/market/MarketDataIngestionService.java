package com.example.portfolio.market;

import com.example.portfolio.market.domain.InstrumentRepository;
import com.example.portfolio.market.persistence.MarketDataStore;
import com.example.portfolio.market.persistence.MarketDataStore.IndicatorSnapshotWrite;
import com.example.portfolio.market.persistence.MarketDataStore.PriceBarWrite;
import com.example.portfolio.market.persistence.ProviderRequestJournal;
import com.example.portfolio.market.provider.MarketDataProvider;
import com.example.portfolio.market.provider.ProviderCallException;
import com.example.portfolio.market.provider.ProviderModels.DailyBar;
import com.example.portfolio.quant.IndicatorResult;
import com.example.portfolio.quant.Indicators;
import com.example.portfolio.quant.QuantBar;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MarketDataIngestionService {
    private static final String NORMALIZATION_VERSION = "v1";

    private final InstrumentRepository instruments;
    private final MarketDataProvider provider;
    private final MarketDataStore store;
    private final ProviderRequestJournal journal;
    private final Clock clock;

    public MarketDataIngestionService(
            InstrumentRepository instruments,
            MarketDataProvider provider,
            MarketDataStore store,
            ProviderRequestJournal journal,
            Clock clock) {
        this.instruments = instruments;
        this.provider = provider;
        this.store = store;
        this.journal = journal;
        this.clock = clock;
    }

    public IngestionResult ingestDailyBars(String symbol, LocalDate from, LocalDate to) {
        var instrument = instruments
                .findFirstBySymbolIgnoreCaseAndActiveTrue(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Unknown active instrument: " + symbol));
        var requestKey = sha256(provider.providerId() + ":daily-bars:" + instrument.symbol() + ":" + from + ":" + to);
        var context = "{\"symbol\":\"" + instrument.symbol() + "\",\"from\":\"" + from + "\",\"to\":\"" + to + "\"}";
        journal.start(requestKey, provider.providerId(), "daily-bars", context);
        final com.example.portfolio.market.provider.ProviderModels.DailyBarsResult result;
        try {
            result = provider.fetchDailyBars(instrument.symbol(), from, to);
            journal.succeed(
                    requestKey,
                    result.provenance().sourceTimestamp(),
                    result.provenance().checksum(),
                    result.provenance().qualityStatus().name(),
                    "{\"barCount\":" + result.bars().size() + "}");
        } catch (ProviderCallException exception) {
            journal.fail(requestKey, exception.httpStatus(), exception.code(), exception.getMessage());
            throw exception;
        }
        var now = clock.instant();
        var writes = result.bars().stream()
                .map(bar -> toWrite(instrument.id(), bar, result.provenance(), now))
                .toList();
        int affectedBars = store.upsertBars(writes);

        var quantBars = result.bars().stream()
                .map(bar -> new QuantBar(
                        bar.marketDate(),
                        bar.open(),
                        bar.high(),
                        bar.low(),
                        bar.close(),
                        bar.volume(),
                        bar.completed()))
                .toList();
        var completed = result.bars().stream().filter(DailyBar::completed).toList();
        if (completed.isEmpty())
            return new IngestionResult(
                    affectedBars, 0, result.provenance().qualityStatus().name());
        var marketDate = completed.getLast().marketDate();
        var sourceChecksum = result.provenance().checksum();
        var snapshots = new ArrayList<IndicatorSnapshotWrite>();
        snapshots.add(snapshot(
                instrument.id(),
                marketDate,
                "SMA_20",
                "period=20",
                true,
                Indicators.sma(quantBars, 20),
                sourceChecksum,
                now));
        snapshots.add(snapshot(
                instrument.id(),
                marketDate,
                "EMA_20",
                "period=20",
                true,
                Indicators.ema(quantBars, 20),
                sourceChecksum,
                now));
        snapshots.add(snapshot(
                instrument.id(),
                marketDate,
                "ATR_14",
                "period=14;method=wilder",
                true,
                Indicators.wilderAtr(quantBars, 14),
                sourceChecksum,
                now));
        snapshots.add(snapshot(
                instrument.id(),
                marketDate,
                "RSI_14",
                "period=14;method=wilder",
                true,
                Indicators.rsi(quantBars, 14),
                sourceChecksum,
                now));
        snapshots.add(snapshot(
                instrument.id(),
                marketDate,
                "ROLLING_HIGH_63",
                "period=63",
                true,
                Indicators.rollingHigh(quantBars, 63),
                sourceChecksum,
                now));
        snapshots.add(snapshot(
                instrument.id(),
                marketDate,
                "REALIZED_VOL_20",
                "period=20;annualization=252",
                true,
                Indicators.realizedVolatility(quantBars, 20),
                sourceChecksum,
                now));
        snapshots.add(snapshot(
                instrument.id(),
                marketDate,
                "REALIZED_VOL_60",
                "period=60;annualization=252",
                true,
                Indicators.realizedVolatility(quantBars, 60),
                sourceChecksum,
                now));
        snapshots.add(macdSnapshot(instrument.id(), marketDate, quantBars, sourceChecksum, now));
        int affectedSnapshots = store.appendIndicatorSnapshots(snapshots);
        return new IngestionResult(
                affectedBars,
                affectedSnapshots,
                result.provenance().qualityStatus().name());
    }

    private static PriceBarWrite toWrite(
            UUID instrumentId,
            DailyBar bar,
            com.example.portfolio.market.provider.ProviderModels.Provenance provenance,
            Instant now) {
        String checksum = sha256(provenance.checksum() + ":" + bar.marketDate() + ":" + bar.adjusted());
        return new PriceBarWrite(
                UUID.randomUUID(),
                instrumentId,
                "1D",
                bar.marketDate().atStartOfDay().toInstant(ZoneOffset.UTC),
                bar.marketDate(),
                bar.open(),
                bar.high(),
                bar.low(),
                bar.close(),
                bar.volume(),
                bar.adjusted(),
                provenance.provider(),
                provenance.sourceTimestamp(),
                checksum,
                provenance.normalizationVersion(),
                provenance.qualityStatus().name(),
                provenance.fetchedAt(),
                "{}",
                now);
    }

    private static IndicatorSnapshotWrite snapshot(
            UUID instrumentId,
            LocalDate marketDate,
            String code,
            String parameters,
            boolean adjusted,
            IndicatorResult<Double> result,
            String sourceChecksum,
            Instant now) {
        return new IndicatorSnapshotWrite(
                UUID.randomUUID(),
                instrumentId,
                marketDate,
                code,
                sha256(parameters),
                adjusted,
                result.status().name(),
                result.value().orElse(null),
                null,
                result.requiredObservations(),
                result.actualObservations(),
                jsonArray(result.warnings()),
                sourceChecksum,
                NORMALIZATION_VERSION,
                now,
                now);
    }

    private static IndicatorSnapshotWrite macdSnapshot(
            UUID instrumentId, LocalDate marketDate, List<QuantBar> bars, String sourceChecksum, Instant now) {
        var result = Indicators.macd(bars, 12, 26, 9);
        var value = result.value().map(Indicators.Macd::line).orElse(null);
        var values = result.value()
                .map(macd -> "{\"line\":" + macd.line() + ",\"signal\":" + macd.signal() + ",\"histogram\":"
                        + macd.histogram() + "}")
                .orElse(null);
        return new IndicatorSnapshotWrite(
                UUID.randomUUID(),
                instrumentId,
                marketDate,
                "MACD_12_26_9",
                sha256("fast=12;slow=26;signal=9"),
                true,
                result.status().name(),
                value,
                values,
                result.requiredObservations(),
                result.actualObservations(),
                jsonArray(result.warnings()),
                sourceChecksum,
                NORMALIZATION_VERSION,
                now,
                now);
    }

    private static String jsonArray(List<String> warnings) {
        return warnings.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record IngestionResult(int affectedBars, int affectedIndicatorSnapshots, String qualityStatus) {}
}
