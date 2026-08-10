package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DecisionPriceWithoutBidAskIsHealthyTest {
    @Test
    void missingExecutionLiquidityDoesNotDowngradeDecisionPrice() {
        var policy = new MarketSessionFreshnessPolicy(new UsEquityTradingCalendar());
        var now = Instant.parse("2026-08-07T21:30:00Z");
        var decisionQuality = policy.decisionPriceQuality(LocalDate.parse("2026-08-07"), new BigDecimal("101.25"), now);
        var liquidity = ProviderModels.ExecutionLiquidityEvidence.from(null, null, decisionQuality);

        assertThat(decisionQuality).isEqualTo(ProviderModels.QualityStatus.HEALTHY);
        assertThat(liquidity.quality()).isEqualTo(ProviderModels.QualityStatus.MISSING);
    }
}
