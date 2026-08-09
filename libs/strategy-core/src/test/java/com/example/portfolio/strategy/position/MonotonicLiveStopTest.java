package com.example.portfolio.strategy.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MonotonicLiveStopTest {
    @Test
    void liveStopNeverMovesBelowPreviousLiveStop() {
        var result =
                StopEngine.calculate(StopEnginePolicyParityTest.input(HoldingClassification.QUALITY_STOCK, "60", "92"));

        assertThat(result.liveStop()).isGreaterThanOrEqualTo(result.initialStop());
        assertThat(result.liveStop()).isGreaterThanOrEqualTo(new BigDecimal("92"));
    }
}
