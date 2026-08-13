package com.example.portfolio.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.dip.CashflowAllocator;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.StopEngine;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class StrategyParameterizationTest {
    @Test
    void stopAndCashflowDecisionsConsumeProvidedStrategyPolicy() {
        var stop = StopEngine.calculate(
                new StopEngine.Input(
                        HoldingClassification.TACTICAL_STOCK,
                        bd("100"),
                        bd("98"),
                        bd("2"),
                        null,
                        null,
                        bd("97"),
                        bd("98"),
                        bd("99"),
                        bd("105"),
                        null),
                new StopEngine.Policy(bd("0.5"), bd("2"), bd("4"), bd("5"), bd("1"), bd("1.5"), bd("2")));

        assertThat(stop.structureStop()).isEqualByComparingTo("97");
        assertThat(stop.volatilityStop()).isEqualByComparingTo("92");

        var plan = CashflowAllocator.allocate(
                bd("10000"),
                bd("5000"),
                bd("20000"),
                true,
                new CashflowAllocator.Policy(
                        bd("20000"), bd("0.40"), bd("0.30"), bd("0.20"), bd("0.10"), bd("0.20"), bd("0.20")));

        assertThat(plan.broadCore()).isEqualByComparingTo("1500");
        assertThat(plan.qualityOpportunity()).isEqualByComparingTo("1000");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
