package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MarketSessionFreshnessPolicyTest {
    private final MarketSessionFreshnessPolicy policy = new MarketSessionFreshnessPolicy(new UsEquityTradingCalendar());

    @Test
    void latestCompletedMarketSessionIsHealthy() {
        var quality = policy.decisionPriceQuality(
                LocalDate.parse("2026-08-07"), new BigDecimal("101.25"), Instant.parse("2026-08-09T18:00:00Z"));

        assertThat(quality).isEqualTo(ProviderModels.QualityStatus.HEALTHY);
    }
}
