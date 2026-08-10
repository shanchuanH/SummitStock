package com.example.portfolio.earnings;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.EarningsPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SpeculativeBinaryEventPolicyTest {
    @Test
    void exitsBinarySpeculationWithoutOneRCushion() {
        var result = EarningsPolicy.review(new EarningsPolicy.Input(
                HoldingClassification.SPECULATIVE,
                8,
                new BigDecimal("0.5"),
                false,
                EarningsPolicy.EventRisk.EXTREME,
                true));

        assertThat(result.action()).isEqualTo("EXIT_BEFORE_EVENT");
    }
}
