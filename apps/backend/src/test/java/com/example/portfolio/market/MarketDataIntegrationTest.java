package com.example.portfolio.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import com.example.portfolio.market.domain.InstrumentRepository;
import com.example.portfolio.market.persistence.MarketDataStore;
import com.example.portfolio.market.persistence.MarketDataStore.PriceBarWrite;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class MarketDataIntegrationTest extends MySqlIntegrationTest {
    @Autowired
    private MarketDataIngestionService ingestion;

    @Autowired
    private MarketDataStore store;

    @Autowired
    private InstrumentRepository instruments;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void migrationAndPipelineAreTraceableBatchSafeAndIdempotent() {
        var tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")
                .query(String.class)
                .list();
        assertThat(tables)
                .contains(
                        "instrument",
                        "price_bar",
                        "quote",
                        "corporate_action",
                        "provider_request",
                        "data_quality_event",
                        "fundamental_observation",
                        "company_event",
                        "indicator_snapshot");

        var to = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        var from = to.minusDays(120);
        var first = ingestion.ingestDailyBars("SPY", from, to);
        var barCount = count("price_bar");
        var snapshotCount = count("indicator_snapshot");

        assertThat(first.affectedBars()).isPositive();
        assertThat(first.affectedIndicatorSnapshots()).isEqualTo(8);
        assertThat(store.bars("SPY", true, 500)).hasSizeGreaterThanOrEqualTo(80);
        assertThat(store.indicators("SPY", 20))
                .extracting(MarketDataStore.IndicatorView::indicatorCode)
                .contains("SMA_20", "EMA_20", "ATR_14", "RSI_14", "MACD_12_26_9");

        ingestion.ingestDailyBars("SPY", from, to);
        assertThat(count("price_bar")).isEqualTo(barCount);
        assertThat(count("indicator_snapshot")).isEqualTo(snapshotCount);
        assertThat(count("provider_request")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT attempt_count FROM provider_request")
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);

        var instrument =
                instruments.findFirstBySymbolIgnoreCaseAndActiveTrue("SPY").orElseThrow();
        var date = from.minusDays(1);
        var at = date.atStartOfDay().toInstant(ZoneOffset.UTC);
        store.upsertBars(List.of(new PriceBarWrite(
                UUID.randomUUID(),
                instrument.id(),
                "1D",
                at,
                date,
                new BigDecimal("90.00"),
                new BigDecimal("91.00"),
                new BigDecimal("89.00"),
                new BigDecimal("90.50"),
                new BigDecimal("1000"),
                false,
                "test-raw",
                at,
                "raw-checksum",
                "v1",
                "VALID",
                Instant.now(),
                "{}",
                Instant.now())));
        assertThat(store.bars("SPY", false, 10)).singleElement().satisfies(bar -> {
            assertThat(bar.adjusted()).isFalse();
            assertThat(bar.closePrice()).isEqualByComparingTo("90.50");
            assertThat(bar.provider()).isEqualTo("test-raw");
        });
        assertThat(store.dataHealth().latestPriceDataAsOf()).isNotNull();
    }

    private long count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }
}
