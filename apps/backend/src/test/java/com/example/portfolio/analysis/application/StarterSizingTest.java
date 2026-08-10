package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import org.junit.jupiter.api.Test;

class StarterSizingTest {
    @Test
    void starterFractionScalesEveryBuyCapacity() {
        var result = PositionSizing.calculate(PositionSizingV2Fixtures.valid(RecommendationAction.STARTER_BUY));

        assertThat(result.quantityMax()).isEqualByComparingTo("7");
        assertThat(result.quantityByRisk()).isEqualByComparingTo("10");
        assertThat(result.quantityByAvailableCash()).isEqualByComparingTo("7");
    }
}
