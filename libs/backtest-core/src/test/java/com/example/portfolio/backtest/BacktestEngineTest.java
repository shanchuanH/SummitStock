package com.example.portfolio.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.portfolio.backtest.BacktestEngine.Action;
import com.example.portfolio.backtest.BacktestEngine.Bar;
import com.example.portfolio.backtest.BacktestEngine.CorporateAction;
import com.example.portfolio.backtest.BacktestEngine.CorporateActionType;
import com.example.portfolio.backtest.BacktestEngine.Lifecycle;
import com.example.portfolio.backtest.BacktestEngine.Request;
import com.example.portfolio.backtest.BacktestEngine.Signal;
import com.example.portfolio.backtest.BacktestEngine.Sleeve;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestEngineTest {
    private final BacktestEngine engine = new BacktestEngine();
    private final LocalDate day1 = LocalDate.parse("2025-01-02");

    @Test
    void executesOnlyAtNextOpenWithSlippageAndGap() {
        var result = engine.replay(request(
                List.of(bar(day1, "100"), bar(day1.plusDays(1), "90")),
                List.of(new Signal("s1", "QQQ", day1, Action.BUY, Sleeve.CORE, bd("2"), "WATCH")),
                List.of()));
        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.fillDate()).isEqualTo(day1.plusDays(1));
            assertThat(trade.fillPrice()).isEqualByComparingTo("90.090000");
        });
    }

    @Test
    void adjustsSharesForSplitAndCreditsCashDividend() {
        var result = engine.replay(request(
                List.of(
                        bar(day1, "100"),
                        bar(day1.plusDays(1), "100"),
                        bar(day1.plusDays(2), "50"),
                        bar(day1.plusDays(3), "51")),
                List.of(new Signal("s1", "QQQ", day1, Action.BUY, Sleeve.ACTIVE, bd("2"), "WATCH")),
                List.of(
                        new CorporateAction("QQQ", day1.plusDays(2), CorporateActionType.SPLIT, bd("2")),
                        new CorporateAction("QQQ", day1.plusDays(3), CorporateActionType.CASH_DIVIDEND, bd("1")))));
        assertThat(result.positions().get("QQQ")).isEqualByComparingTo("4");
        assertThat(result.endingCash()).isEqualByComparingTo("803.80");
    }

    @Test
    void clampsQuantityAndMarksComplianceWithoutMixingSleeves() {
        var result = engine.replay(request(
                List.of(bar(day1, "10"), bar(day1.plusDays(1), "10")),
                List.of(new Signal("s1", "QQQ", day1, Action.BUY, Sleeve.ACTIVE, bd("20"), "MUST_ACT")),
                List.of()));
        assertThat(result.quantityCompliant()).isFalse();
        assertThat(result.trades()).singleElement().satisfies(trade -> {
            assertThat(trade.quantity()).isEqualByComparingTo("10");
            assertThat(trade.sleeve()).isEqualTo(Sleeve.ACTIVE);
        });
    }

    @Test
    void rejectsIncompleteBarsSurvivorshipAndOosLeakage() {
        var incomplete = new Bar("QQQ", day1, bd("10"), bd("10"), bd("10"), bd("10"), false);
        assertThatThrownBy(() -> engine.replay(request(List.of(incomplete), List.of(), List.of())))
                .hasMessage("BACKTEST_COMPLETED_BARS_ONLY");
        var biased = request(List.of(bar(day1, "10")), List.of(), List.of(), false, day1, day1);
        assertThatThrownBy(() -> engine.replay(biased)).hasMessage("BACKTEST_SURVIVORSHIP_BIAS");
        var leakage = request(List.of(bar(day1, "10")), List.of(), List.of(), true, day1, day1);
        assertThatThrownBy(() -> engine.replay(leakage)).hasMessage("BACKTEST_OOS_LEAKAGE");
    }

    private Request request(List<Bar> bars, List<Signal> signals, List<CorporateAction> actions) {
        return request(bars, signals, actions, true, day1, day1.plusDays(1));
    }

    private Request request(
            List<Bar> bars,
            List<Signal> signals,
            List<CorporateAction> actions,
            boolean includeDelisted,
            LocalDate trainingThrough,
            LocalDate oosFrom) {
        return new Request(
                bd("1000"),
                bd("10"),
                bd("10"),
                includeDelisted,
                trainingThrough,
                oosFrom,
                bars,
                signals,
                actions,
                List.of(new Lifecycle("QQQ", day1.minusYears(1), day1.plusYears(10), true)));
    }

    private Bar bar(LocalDate date, String open) {
        return new Bar("QQQ", date, bd(open), bd(open), bd(open), bd(open), true);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
