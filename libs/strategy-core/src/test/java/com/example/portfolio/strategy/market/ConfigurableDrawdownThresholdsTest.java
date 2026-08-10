package com.example.portfolio.strategy.market;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ConfigurableDrawdownThresholdsTest {
    @Test
    void classificationUsesRuntimeThresholdsInsteadOfEmbeddedPercentages() {
        var input = new DrawdownEngine.Input(
                new BigDecimal("91"),
                new BigDecimal("100"),
                -0.03,
                -0.04,
                0.60,
                0.20,
                0.20,
                0.20,
                EvidenceQuality.HEALTHY);
        var thresholds = new DrawdownEngine.Thresholds(0.05, 0.07, 0.11, 0.14, 0.18);

        var result = DrawdownEngine.classify(input, thresholds);

        assertThat(result.state()).isEqualTo(DrawdownEngine.State.REDUCE_TACTICAL);
    }
}
