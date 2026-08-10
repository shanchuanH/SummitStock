package com.example.portfolio.strategy.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import org.junit.jupiter.api.Test;

class CatastrophicAtrTest {
    @Test
    void catastrophicLineIsThreeQuarterAtrBelowLiveStop() {
        var result =
                StopEngine.calculate(StopEnginePolicyParityTest.input(HoldingClassification.SPECULATIVE, "80", "70"));

        assertThat(result.liveStop().subtract(result.catastrophicStop())).isEqualByComparingTo("3");
    }
}
