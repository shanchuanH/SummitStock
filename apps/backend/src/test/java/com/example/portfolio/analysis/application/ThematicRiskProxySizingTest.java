package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ThematicRiskProxySizingTest {
    @Test
    void etfWithoutOrdinaryStopStillConsumesPortfolioAndClusterRisk() {
        var input = new PositionSizing.Input(
                RecommendationAction.ADD,
                bd("100000"),
                bd("0.005"),
                bd("100"),
                null,
                bd("100"),
                bd("50"),
                bd("5000"),
                bd("0.05"),
                bd("0.20"),
                bd("0.15"),
                bd("10000"),
                bd("500"),
                bd("0.0075"),
                BigDecimal.ONE,
                false,
                EvidenceQuality.HEALTHY,
                EvidenceQuality.HEALTHY,
                EvidenceQuality.HEALTHY,
                true,
                true,
                true,
                false,
                bd("1800"),
                bd("0.0200"),
                bd("1000"),
                bd("10"));

        var result = PositionSizing.calculate(input);

        assertThat(result.quantityByRisk()).isEqualByComparingTo("50");
        assertThat(result.quantityByTotalRiskCap()).isEqualByComparingTo("20");
        assertThat(result.quantityByClusterCap()).isEqualByComparingTo("25");
        assertThat(result.quantityMax()).isEqualByComparingTo("20");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
