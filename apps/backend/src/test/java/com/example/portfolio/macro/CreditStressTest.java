package com.example.portfolio.macro;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class CreditStressTest {
    @Test
    void highYieldSpreadTailProducesHighCreditStress() {
        var result = new MacroFactorEngine()
                .evaluate(new MacroFactorEngine.Input(
                        null,
                        List.of(),
                        MacroFactorEngineTest.bd("8"),
                        List.of(
                                MacroFactorEngineTest.bd("3"),
                                MacroFactorEngineTest.bd("4"),
                                MacroFactorEngineTest.bd("5"),
                                MacroFactorEngineTest.bd("8")),
                        null,
                        null,
                        null,
                        null,
                        null));

        assertThat(result.creditStress()).isEqualByComparingTo("1");
        assertThat(result.stressResilience()).isEqualByComparingTo("0");
    }
}
