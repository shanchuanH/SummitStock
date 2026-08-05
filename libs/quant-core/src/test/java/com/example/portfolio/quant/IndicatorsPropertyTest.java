package com.example.portfolio.quant;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.DoubleRange;
import net.jqwik.api.constraints.IntRange;

class IndicatorsPropertyTest {
    @Property(tries = 100)
    void constantSeriesKeepsMovingAveragesConstant(
            @ForAll @DoubleRange(min = 0.01, max = 10000) double value,
            @ForAll @IntRange(min = 2, max = 30) int period) {
        var bars = constantBars(value, period + 10);
        assertThat(Indicators.sma(bars, period).value().orElseThrow()).isCloseTo(value, within(1e-9));
        assertThat(Indicators.ema(bars, period).value().orElseThrow()).isCloseTo(value, within(1e-9));
    }

    @Property(tries = 100)
    void rsiAndRollingHighStayInsideMathematicalBounds(
            @ForAll @DoubleRange(min = 1, max = 1000) double start,
            @ForAll @DoubleRange(min = -0.05, max = 0.05) double step) {
        var bars = new ArrayList<QuantBar>();
        double price = start;
        for (int day = 1; day <= 30; day++) {
            price = Math.max(0.01, price * (1 + step));
            var value = BigDecimal.valueOf(price);
            bars.add(new QuantBar(
                    LocalDate.of(2026, 1, 1).plusDays(day - 1L), value, value, value, value, BigDecimal.ONE, true));
        }
        assertThat(Indicators.rsi(bars, 14).value().orElseThrow()).isBetween(0.0, 100.0);
        assertThat(Indicators.rollingHigh(bars, 20).value().orElseThrow())
                .isGreaterThanOrEqualTo(bars.getLast().close().doubleValue());
    }

    private static ArrayList<QuantBar> constantBars(double value, int count) {
        var result = new ArrayList<QuantBar>();
        var price = BigDecimal.valueOf(value);
        for (int day = 1; day <= count; day++) {
            result.add(new QuantBar(
                    LocalDate.of(2026, 1, 1).plusDays(day - 1L), price, price, price, price, BigDecimal.ONE, true));
        }
        return result;
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
