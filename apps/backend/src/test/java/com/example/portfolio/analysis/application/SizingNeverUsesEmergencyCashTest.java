package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import org.junit.jupiter.api.Test;

class SizingNeverUsesEmergencyCashTest {
    @Test
    void deployableCashIsTheOnlyCashCapacity() {
        var result = PositionSizing.calculate(PositionSizingV2Fixtures.valid(RecommendationAction.ADD));

        assertThat(result.quantityByAvailableCash()).isEqualByComparingTo("30");
        assertThat(result.quantityMax()).isEqualByComparingTo("30");
    }
}
