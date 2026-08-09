package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.market.EvidenceQuality;
import org.junit.jupiter.api.Test;

class TrimQuantityTest {
    @Test
    void trimUsesHardCapExcessInsteadOfBuyRiskFormula() {
        var input = PositionSizingV2Fixtures.input(
                RecommendationAction.TRIM,
                "80000",
                "3000",
                "800",
                EvidenceQuality.HEALTHY,
                true,
                "90",
                "160",
                "16000",
                "0.15",
                "0.15",
                "0.25");

        var result = PositionSizing.calculate(input);

        assertThat(result.quantityMin()).isEqualByComparingTo("40");
        assertThat(result.quantityMax()).isEqualByComparingTo("40");
        assertThat(result.quantityByRisk()).isNull();
    }
}
