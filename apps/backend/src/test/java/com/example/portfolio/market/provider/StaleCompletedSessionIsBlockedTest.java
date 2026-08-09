package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class StaleCompletedSessionIsBlockedTest {
    @Test
    void priorCompletedSessionIsStaleAfterTheNextSessionCloses() {
        var policy = new MarketSessionFreshnessPolicy(new UsEquityTradingCalendar());

        var quality = policy.decisionPriceQuality(
                LocalDate.parse("2026-08-06"), new BigDecimal("101.25"), Instant.parse("2026-08-07T21:30:00Z"));

        assertThat(quality).isEqualTo(ProviderModels.QualityStatus.STALE);
    }
}
