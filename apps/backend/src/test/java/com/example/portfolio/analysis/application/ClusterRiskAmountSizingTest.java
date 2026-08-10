package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.market.EvidenceQuality;
import org.junit.jupiter.api.Test;

class ClusterRiskAmountSizingTest {
    @Test
    void clusterCapacityIsRemainingRiskAmountDividedByRiskPerShare() {
        var input = PositionSizingV2Fixtures.input(
                RecommendationAction.ADD,
                "80000",
                "3000",
                "1500",
                EvidenceQuality.HEALTHY,
                true,
                "90",
                "60",
                "6000",
                "0.15",
                "0.15",
                "0.25");

        var result = PositionSizing.calculate(input);

        assertThat(result.quantityByClusterCap()).isEqualByComparingTo("10");
        assertThat(result.quantityMax()).isEqualByComparingTo("10");
    }
}
