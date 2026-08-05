package com.example.portfolio.strategy.dip;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EtfDipEngineTest {
    @Test
    void requiresMarketDrivenDrawdownAndTwoTriggers() {
        assertThat(EtfDipEngine.evaluate(input(false, 2, 0, 5)).action()).isEqualTo("WATCH");
        assertThat(EtfDipEngine.evaluate(input(true, 1, 0, 5)).action()).isEqualTo("WAIT_FOR_CONFIRMATION");
    }

    @Test
    void deploysFourUniqueFractionsWithCooldown() {
        assertThat(EtfDipEngine.evaluate(input(true, 2, 0, 5)).reserveFraction())
                .isEqualByComparingTo("0.20");
        assertThat(EtfDipEngine.evaluate(input(true, 2, 1, 4)).action()).isEqualTo("COOLDOWN");
        assertThat(EtfDipEngine.evaluate(input(true, 2, 1, 5)).reserveFraction())
                .isEqualByComparingTo("0.25");
        assertThat(EtfDipEngine.evaluate(input(true, 2, 2, 5)).reserveFraction())
                .isEqualByComparingTo("0.30");
        assertThat(EtfDipEngine.evaluate(input(true, 2, 3, 5)).reserveFraction())
                .isEqualByComparingTo("0.25");
    }

    @Test
    void emergencyCashAndNoSignalFallbackAreDeterministic() {
        var emergency = CashflowAllocator.allocate(bd("11000"), bd("4000"), bd("18000"), true);
        assertThat(emergency.emergency()).isEqualByComparingTo("2000");
        assertThat(emergency.broadCore()).isEqualByComparingTo("5000");
        var fallback = CashflowAllocator.allocate(bd("11000"), bd("4000"), bd("20000"), false);
        assertThat(fallback.broadCore()).isEqualByComparingTo("4200");
        assertThat(fallback.qualityOpportunity()).isZero();
    }

    @Test
    void activeSleeveBudgetDropsAtTwelveAndTwentyFourMonths() {
        assertThat(ActiveSleeveAccountability.review(12, bd("0.06"), false).budgetMultiplier())
                .isEqualByComparingTo("0.75");
        assertThat(ActiveSleeveAccountability.review(24, bd("0.06"), false).budgetMultiplier())
                .isEqualByComparingTo("0.5");
    }

    private static EtfDipEngine.Input input(boolean market, int triggers, int completed, int days) {
        return new EtfDipEngine.Input(
                bd("0.15"),
                market,
                true,
                true,
                1,
                1,
                1,
                1,
                1,
                1,
                triggers >= 1,
                triggers >= 2,
                false,
                false,
                false,
                false,
                completed,
                days);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
