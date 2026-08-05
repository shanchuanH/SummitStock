package com.example.portfolio.strategy.position;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PositionIntelligenceTest {
    @Test
    void liveStopNeverMovesDownAndEmitsCloseConfirmation() {
        var result = StopEngine.calculate(
                input(HoldingClassification.QUALITY_STOCK, "100", "92", "4", "94", "93", "96", "95", "93"));
        assertThat(result.initialStop()).isEqualByComparingTo("90");
        assertThat(result.liveStop()).isEqualByComparingTo("94");
        assertThat(result.closeConfirmed()).isTrue();
    }

    @Test
    void coreEtfDoesNotUseOrdinaryStockStop() {
        assertThat(StopEngine.calculate(input(
                                HoldingClassification.CORE_BROAD_ETF, "100", "92", "4", "94", "93", "96", "95", "93"))
                        .ordinaryStopApplicable())
                .isFalse();
    }

    @Test
    void calculatesRealizedRAndExcursionsWithoutDouble() {
        var metrics = TradeExcursion.calculate(bd("100"), bd("90"), bd("125"), bd("85"), bd("115"));
        assertThat(metrics.realizedR()).isEqualByComparingTo("1.5");
        assertThat(metrics.mfeR()).isEqualByComparingTo("2.5");
        assertThat(metrics.maeR()).isEqualByComparingTo("-1.5");
    }

    @Test
    void cheapQualityWithFallingRevisionsIsNotBought() {
        var result =
                QualityDiscountEngine.evaluate(new QualityDiscountEngine.Input(true, true, false, true, true, bd("0")));
        assertThat(result.action()).isEqualTo("DO_NOT_ADD");
    }

    @Test
    void stabilizedQualityDiscountUsesSeparateOnePercentStarter() {
        var result =
                QualityDiscountEngine.evaluate(new QualityDiscountEngine.Input(true, true, true, true, true, bd("0")));
        assertThat(result.action()).isEqualTo("ADD_1_PERCENT_STARTER");
        assertThat(result.addWeight()).isEqualByComparingTo("0.01");
    }

    @Test
    void earningsPolicyVariesByClassAndProfitCushion() {
        assertThat(EarningsPolicy.review(
                                new EarningsPolicy.Input(HoldingClassification.SPECULATIVE, 10, bd("2"), false))
                        .action())
                .isEqualTo("EXIT_BEFORE_EVENT");
        assertThat(EarningsPolicy.review(
                                new EarningsPolicy.Input(HoldingClassification.TACTICAL_STOCK, 10, bd("0.5"), false))
                        .action())
                .isEqualTo("REDUCE_HALF");
        assertThat(EarningsPolicy.review(
                                new EarningsPolicy.Input(HoldingClassification.QUALITY_STOCK, 10, bd("2"), false))
                        .action())
                .isEqualTo("HOLD_THROUGH_EVENT");
    }

    private static StopEngine.Input input(
            HoldingClassification classification,
            String entry,
            String swing,
            String atr,
            String previous,
            String chandelier,
            String ema20,
            String higherLow,
            String close) {
        return new StopEngine.Input(
                classification,
                bd(entry),
                bd(swing),
                bd(atr),
                bd(previous),
                bd(chandelier),
                bd(ema20),
                bd(higherLow),
                bd(close));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
