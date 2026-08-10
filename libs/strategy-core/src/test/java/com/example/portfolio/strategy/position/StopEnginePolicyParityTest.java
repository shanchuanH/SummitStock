package com.example.portfolio.strategy.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StopEnginePolicyParityTest {
    @Test
    void strategyEngineOwnsStructureAndVolatilityFormula() {
        var result = StopEngine.calculate(input(HoldingClassification.QUALITY_STOCK, "80", "70"));

        assertThat(result.structureStop()).isEqualByComparingTo("79");
        assertThat(result.volatilityStop()).isEqualByComparingTo("90");
        assertThat(result.initialStop()).isEqualByComparingTo("79");
    }

    static StopEngine.Input input(HoldingClassification classification, String swing, String previous) {
        return new StopEngine.Input(
                classification,
                new BigDecimal("100"),
                new BigDecimal(swing),
                new BigDecimal("4"),
                new BigDecimal(previous),
                null,
                new BigDecimal("88"),
                new BigDecimal("82"),
                new BigDecimal("95"),
                new BigDecimal("110"),
                new BigDecimal("2.5"));
    }
}
