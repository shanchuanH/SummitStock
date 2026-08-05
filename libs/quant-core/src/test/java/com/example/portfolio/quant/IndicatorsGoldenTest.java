package com.example.portfolio.quant;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IndicatorsGoldenTest {
    @Test
    void movingAveragesMatchGoldenSeriesAndIgnoreIncompleteBar() {
        var bars = bars(1, 2, 3, 4, 5);
        bars.add(bar(6, 100, false));

        assertThat(Indicators.sma(bars, 3).value()).contains(4.0);
        assertThat(Indicators.ema(bars, 3).value()).contains(4.0);
    }

    @Test
    void wilderAtrAndRsiHaveExplicitWarmup() {
        assertThat(Indicators.wilderAtr(bars(10, 11, 12), 3).status()).isEqualTo(IndicatorStatus.WARMING_UP);
        assertThat(Indicators.rsi(bars(10, 11, 12, 13), 3).value()).contains(100.0);

        var ranges = List.of(
                ohlc(1, 10, 11, 9, 10), ohlc(2, 10, 12, 9, 11), ohlc(3, 11, 13, 10, 12), ohlc(4, 12, 14, 11, 13));
        assertThat(Indicators.wilderAtr(ranges, 3).value().orElseThrow()).isEqualTo(3.0);
    }

    @Test
    void gapAndSplitSignalsNeverInventMissingData() {
        var instrument = bars(100, 101, 102, 103);
        var benchmark = new ArrayList<>(bars(100, 101, 102, 103));
        benchmark.set(2, new QuantBar(LocalDate.of(2026, 1, 9), bd(102), bd(102), bd(102), bd(102), bd(1000), true));
        assertThat(Indicators.relativeStrength(instrument, benchmark, 3).status())
                .isEqualTo(IndicatorStatus.MISSING_DATA);

        var unadjustedSplit = bars(100, 101, 50, 51);
        var adjusted = bars(50, 50.5, 50, 51);
        assertThat(Indicators.realizedVolatility(unadjustedSplit, 3).value().orElseThrow())
                .isGreaterThan(
                        Indicators.realizedVolatility(adjusted, 3).value().orElseThrow());
    }

    @Test
    void macdRollingHighVolatilityAndRelativeStrengthProduceFiniteGoldenValues() {
        var series = new ArrayList<QuantBar>();
        for (int i = 1; i <= 40; i++) series.add(bar(i, i, true));

        var macd = Indicators.macd(series, 12, 26, 9).value().orElseThrow();
        assertThat(macd.line()).isFinite().isPositive();
        assertThat(macd.signal()).isFinite().isPositive();
        assertThat(Indicators.rollingHigh(series, 20).value()).contains(40.0);
        assertThat(Indicators.realizedVolatility(series, 20).value().orElseThrow())
                .isFinite()
                .isPositive();
        assertThat(Indicators.relativeStrength(series, series, 20).value()).contains(1.0);
    }

    @Test
    void pivotsSeparateCandidateFromConfirmedAndEarningsGapsAreAuditable() {
        var candidate =
                Indicators.latestSwingCandidate(bars(5, 4, 3), 2).value().orElseThrow();
        assertThat(candidate.status()).isEqualTo(Indicators.PivotStatus.CANDIDATE);

        var confirmed =
                Indicators.confirmedSwingLow(bars(5, 4, 3, 4, 5), 2, 2).value().orElseThrow();
        assertThat(confirmed.status()).isEqualTo(Indicators.PivotStatus.CONFIRMED);
        assertThat(confirmed.price()).isEqualTo(3.0);

        var gaps = Indicators.earningsGapStatistics(List.of(
                        new Indicators.EarningsGap(bd(100), bd(110)), new Indicators.EarningsGap(bd(100), bd(95))))
                .value()
                .orElseThrow();
        assertThat(gaps.averageGap()).isCloseTo(0.025, within(1e-12));
        assertThat(gaps.averageAbsoluteGap()).isCloseTo(0.075, within(1e-12));
        assertThat(gaps.positiveCount()).isEqualTo(1);
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }

    private static ArrayList<QuantBar> bars(double... closes) {
        var result = new ArrayList<QuantBar>();
        for (int i = 0; i < closes.length; i++) result.add(bar(i + 1, closes[i], true));
        return result;
    }

    private static QuantBar bar(int day, double close, boolean completed) {
        return ohlc(day, close, close, close, close, completed);
    }

    private static QuantBar ohlc(int day, double open, double high, double low, double close) {
        return ohlc(day, open, high, low, close, true);
    }

    private static QuantBar ohlc(int day, double open, double high, double low, double close, boolean completed) {
        return new QuantBar(
                LocalDate.of(2026, 1, 1).plusDays(day - 1L),
                bd(open),
                bd(high),
                bd(low),
                bd(close),
                bd(1000),
                completed);
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
