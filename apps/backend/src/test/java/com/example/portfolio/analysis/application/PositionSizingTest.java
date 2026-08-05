package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositionSizingTest {
    @Test
    void quantityIsBoundedByRiskWeightCashAndClusterCapacity() {
        var result = PositionSizing.calculate(input(EvidenceQuality.HEALTHY, true, new BigDecimal("90")));
        assertThat(result.exactQuantityAllowed()).isTrue();
        assertThat(result.quantityByRisk()).isEqualByComparingTo("40");
        assertThat(result.quantityMax()).isEqualByComparingTo("20");
    }

    @Test
    void staleQuoteOrMissingFormalStopBlocksFalsePrecision() {
        assertThat(PositionSizing.calculate(input(EvidenceQuality.STALE, true, new BigDecimal("90")))
                        .exactQuantityAllowed())
                .isFalse();
        assertThat(PositionSizing.calculate(input(EvidenceQuality.HEALTHY, false, null))
                        .quantityMax())
                .isNull();
    }

    private static PositionSizing.Input input(EvidenceQuality quality, boolean fresh, BigDecimal stop) {
        return new PositionSizing.Input(
                new BigDecimal("100000"),
                new BigDecimal("100000"),
                new BigDecimal("0.004"),
                new BigDecimal("100"),
                stop,
                new BigDecimal("100"),
                new BigDecimal("6000"),
                new BigDecimal("0.04"),
                new BigDecimal("0.08"),
                new BigDecimal("0.15"),
                new BigDecimal("3000"),
                new BigDecimal("2000"),
                quality,
                fresh);
    }
}
