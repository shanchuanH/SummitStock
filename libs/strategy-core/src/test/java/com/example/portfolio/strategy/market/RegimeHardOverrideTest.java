package com.example.portfolio.strategy.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegimeHardOverrideTest {
    @Test
    void panicEvidenceOverridesOtherwiseStrongComponents() {
        var result = MarketRegimeEngine.classify(new MarketRegimeEngine.Input(
                1, 1, 1, 1, false, false, 35, 0.2, false, 60, false, EvidenceQuality.HEALTHY));

        assertThat(result.label()).isEqualTo(MarketRegimeEngine.Label.RED);
    }
}
