package com.example.portfolio.macro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MacroFactorEngineTest {
    @Test
    void combinesVolatilityCreditRatesCurveAndRealizedVolatility() {
        var result = new MacroFactorEngine()
                .evaluate(new MacroFactorEngine.Input(
                        bd("25"),
                        List.of(bd("10"), bd("20"), bd("30")),
                        bd("5"),
                        List.of(bd("3"), bd("4"), bd("6")),
                        bd("4.5"),
                        bd("3.5"),
                        bd("4.8"),
                        bd("5.25"),
                        bd("0.6")));

        assertThat(result.volatilityStress()).isNotNull();
        assertThat(result.creditStress()).isNotNull();
        assertThat(result.curveState()).isEqualTo(MacroFactorEngine.CurveState.INVERTED);
        assertThat(result.stressResilience()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
        assertThat(result.quality()).isEqualTo(MacroFactorEngine.Quality.HEALTHY);
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
