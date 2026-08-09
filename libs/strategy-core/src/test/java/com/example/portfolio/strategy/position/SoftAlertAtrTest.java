package com.example.portfolio.strategy.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import org.junit.jupiter.api.Test;

class SoftAlertAtrTest {
    @Test
    void softAlertIsHalfAtrAboveLiveStop() {
        var result = StopEngine.calculate(
                StopEnginePolicyParityTest.input(HoldingClassification.TACTICAL_STOCK, "80", "70"));

        assertThat(result.softAlert().subtract(result.liveStop())).isEqualByComparingTo("2");
    }
}
