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

    @Test
    void ratesAndCurveRemainContextAndDoNotChangeAggregateStress() {
        var calmRates = evaluateRates("3.5", "3.5", "3.0", "3.0");
        var stressedRates = evaluateRates("6.0", "3.0", "6.5", "5.5");

        assertThat(stressedRates.rateStress()).isGreaterThan(calmRates.rateStress());
        assertThat(stressedRates.curveState()).isEqualTo(MacroFactorEngine.CurveState.INVERTED);
        assertThat(stressedRates.stressResilience()).isEqualByComparingTo(calmRates.stressResilience());
    }

    private static MacroFactorEngine.Result evaluateRates(
            String tenYear, String priorTenYear, String twoYear, String fedFunds) {
        return new MacroFactorEngine()
                .evaluate(new MacroFactorEngine.Input(
                        bd("20"),
                        List.of(bd("15"), bd("20"), bd("25")),
                        bd("4"),
                        List.of(bd("3"), bd("4"), bd("5")),
                        bd(tenYear),
                        bd(priorTenYear),
                        bd(twoYear),
                        bd(fedFunds),
                        bd("0.5")));
    }

    static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
