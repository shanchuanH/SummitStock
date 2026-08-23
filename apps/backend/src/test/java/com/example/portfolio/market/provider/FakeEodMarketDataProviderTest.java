package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FakeEodMarketDataProviderTest {
    @Test
    void weekendRangeUsesLatestRequestedTradingSessionForProvenance() {
        var clock = Clock.fixed(Instant.parse("2026-08-23T08:00:00Z"), ZoneOffset.UTC);
        var provider = new FakeEodMarketDataProvider(clock, FakeEodMarketDataProvider.FailureMode.NONE);

        var result = provider.fetchDailyBars("SPY", LocalDate.parse("2026-08-17"), LocalDate.parse("2026-08-22"));

        assertThat(result.bars())
                .extracting(ProviderModels.DailyBar::marketDate)
                .last()
                .isEqualTo(LocalDate.parse("2026-08-21"));
        assertThat(result.provenance().sourceTimestamp()).isEqualTo(Instant.parse("2026-08-21T20:00:00Z"));
    }
}
