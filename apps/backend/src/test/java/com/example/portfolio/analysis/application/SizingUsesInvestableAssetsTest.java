package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import org.junit.jupiter.api.Test;

class SizingUsesInvestableAssetsTest {
    @Test
    void riskAndWeightUseInvestableAssetsInsteadOfTotalLiquidAssets() {
        var result = PositionSizing.calculate(PositionSizingV2Fixtures.valid(RecommendationAction.ADD));

        assertThat(result.exactQuantityAllowed()).isTrue();
        assertThat(result.quantityByRisk()).isEqualByComparingTo("40");
        assertThat(result.quantityByWeightCap()).isEqualByComparingTo("60");
    }
}
