package com.example.portfolio.strategy.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import org.junit.jupiter.api.Test;

class CoreEtfNoOrdinaryStopTest {
    @Test
    void coreEtfHasNoOrdinaryStop() {
        var result = StopEngine.calculate(
                StopEnginePolicyParityTest.input(HoldingClassification.CORE_BROAD_ETF, "80", "70"));

        assertThat(result.ordinaryStopApplicable()).isFalse();
        assertThat(result.liveStop()).isNull();
    }
}
