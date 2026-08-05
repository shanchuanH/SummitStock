package com.example.portfolio.quant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class Indicators {
    private static final double TRADING_DAYS = 252.0;

    private Indicators() {}

    public static IndicatorResult<Double> sma(List<QuantBar> input, int period) {
        var bars = completed(input);
        var invalid = Indicators.<Double>validate(bars, period);
        if (invalid.isPresent()) return invalid.get();
        return IndicatorResult.ready(
                bars.subList(bars.size() - period, bars.size()).stream()
                        .mapToDouble(bar -> bar.close().doubleValue())
                        .average()
                        .orElseThrow(),
                period,
                bars.size());
    }

    public static IndicatorResult<Double> ema(List<QuantBar> input, int period) {
        var bars = completed(input);
        var invalid = Indicators.<Double>validate(bars, period);
        if (invalid.isPresent()) return invalid.get();
        return IndicatorResult.ready(emaValue(closes(bars), period), period, bars.size());
    }

    public static IndicatorResult<Double> wilderAtr(List<QuantBar> input, int period) {
        var bars = completed(input);
        int required = period + 1;
        var invalid = Indicators.<Double>validate(bars, required);
        if (invalid.isPresent()) return invalid.get();
        var trueRanges = new ArrayList<Double>();
        for (int i = 1; i < bars.size(); i++) {
            var current = bars.get(i);
            double previousClose = bars.get(i - 1).close().doubleValue();
            trueRanges.add(Math.max(
                    current.high().doubleValue() - current.low().doubleValue(),
                    Math.max(
                            Math.abs(current.high().doubleValue() - previousClose),
                            Math.abs(current.low().doubleValue() - previousClose))));
        }
        double atr = trueRanges.subList(0, period).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        for (int i = period; i < trueRanges.size(); i++) {
            atr = ((atr * (period - 1)) + trueRanges.get(i)) / period;
        }
        return IndicatorResult.ready(atr, required, bars.size());
    }

    public static IndicatorResult<Double> rsi(List<QuantBar> input, int period) {
        var bars = completed(input);
        int required = period + 1;
        var invalid = Indicators.<Double>validate(bars, required);
        if (invalid.isPresent()) return invalid.get();
        double gains = 0;
        double losses = 0;
        for (int i = 1; i <= period; i++) {
            double change =
                    bars.get(i).close().doubleValue() - bars.get(i - 1).close().doubleValue();
            gains += Math.max(change, 0);
            losses += Math.max(-change, 0);
        }
        double averageGain = gains / period;
        double averageLoss = losses / period;
        for (int i = period + 1; i < bars.size(); i++) {
            double change =
                    bars.get(i).close().doubleValue() - bars.get(i - 1).close().doubleValue();
            averageGain = ((averageGain * (period - 1)) + Math.max(change, 0)) / period;
            averageLoss = ((averageLoss * (period - 1)) + Math.max(-change, 0)) / period;
        }
        double value = averageLoss == 0 ? 100.0 : 100.0 - (100.0 / (1.0 + averageGain / averageLoss));
        return IndicatorResult.ready(value, required, bars.size());
    }

    public static IndicatorResult<Macd> macd(List<QuantBar> input, int fast, int slow, int signal) {
        var bars = completed(input);
        int required = slow + signal - 1;
        var invalid = Indicators.<Macd>validate(bars, required);
        if (invalid.isPresent()) return invalid.get();
        if (fast >= slow) {
            return IndicatorResult.unavailable(
                    IndicatorStatus.INVALID_DATA, required, bars.size(), "fast period must be below slow period");
        }
        var prices = closes(bars);
        var fastSeries = emaSeries(prices, fast);
        var slowSeries = emaSeries(prices, slow);
        var macdSeries = new ArrayList<Double>();
        for (int i = slow - 1; i < prices.size(); i++) {
            macdSeries.add(fastSeries.get(i) - slowSeries.get(i));
        }
        double line = macdSeries.getLast();
        double signalLine = emaValue(macdSeries, signal);
        return IndicatorResult.ready(new Macd(line, signalLine, line - signalLine), required, bars.size());
    }

    public static IndicatorResult<Double> rollingHigh(List<QuantBar> input, int period) {
        var bars = completed(input);
        var invalid = Indicators.<Double>validate(bars, period);
        if (invalid.isPresent()) return invalid.get();
        return IndicatorResult.ready(
                bars.subList(bars.size() - period, bars.size()).stream()
                        .mapToDouble(bar -> bar.high().doubleValue())
                        .max()
                        .orElseThrow(),
                period,
                bars.size());
    }

    public static IndicatorResult<Double> realizedVolatility(List<QuantBar> input, int period) {
        var bars = completed(input);
        int required = period + 1;
        var invalid = Indicators.<Double>validate(bars, required);
        if (invalid.isPresent()) return invalid.get();
        var returns = new ArrayList<Double>();
        for (int i = bars.size() - period; i < bars.size(); i++) {
            double previous = bars.get(i - 1).close().doubleValue();
            double current = bars.get(i).close().doubleValue();
            if (previous <= 0 || current <= 0) {
                return IndicatorResult.unavailable(
                        IndicatorStatus.INVALID_DATA, required, bars.size(), "prices must be positive");
            }
            returns.add(Math.log(current / previous));
        }
        double mean =
                returns.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double variance =
                returns.stream().mapToDouble(value -> Math.pow(value - mean, 2)).sum() / (period - 1);
        return IndicatorResult.ready(Math.sqrt(variance * TRADING_DAYS), required, bars.size());
    }

    public static IndicatorResult<Double> relativeStrength(
            List<QuantBar> instrument, List<QuantBar> benchmark, int lookback) {
        var left = completed(instrument);
        var right = completed(benchmark);
        int required = lookback + 1;
        if (left.size() < required || right.size() < required) {
            return IndicatorResult.unavailable(
                    IndicatorStatus.WARMING_UP,
                    required,
                    Math.min(left.size(), right.size()),
                    "insufficient aligned observations");
        }
        var leftWindow = left.subList(left.size() - required, left.size());
        var rightWindow = right.subList(right.size() - required, right.size());
        for (int i = 0; i < required; i++) {
            if (!leftWindow.get(i).marketDate().equals(rightWindow.get(i).marketDate())) {
                return IndicatorResult.unavailable(
                        IndicatorStatus.MISSING_DATA, required, i, "instrument and benchmark dates are not aligned");
            }
        }
        double instrumentReturn = leftWindow.getLast().close().doubleValue()
                / leftWindow.getFirst().close().doubleValue();
        double benchmarkReturn = rightWindow.getLast().close().doubleValue()
                / rightWindow.getFirst().close().doubleValue();
        return IndicatorResult.ready(instrumentReturn / benchmarkReturn, required, required);
    }

    public static IndicatorResult<SwingLow> confirmedSwingLow(
            List<QuantBar> input, int leftStrength, int rightStrength) {
        var bars = completed(input);
        int required = leftStrength + rightStrength + 1;
        var invalid = Indicators.<SwingLow>validate(bars, required);
        if (invalid.isPresent()) return invalid.get();
        for (int candidate = bars.size() - rightStrength - 1; candidate >= leftStrength; candidate--) {
            double low = bars.get(candidate).low().doubleValue();
            boolean lowerThanLeft = true;
            boolean noHigherRightLow = true;
            for (int i = candidate - leftStrength; i < candidate; i++) {
                lowerThanLeft &= low < bars.get(i).low().doubleValue();
            }
            for (int i = candidate + 1; i <= candidate + rightStrength; i++) {
                noHigherRightLow &= low <= bars.get(i).low().doubleValue();
            }
            if (lowerThanLeft && noHigherRightLow) {
                return IndicatorResult.ready(
                        new SwingLow(bars.get(candidate).marketDate(), low, PivotStatus.CONFIRMED),
                        required,
                        bars.size());
            }
        }
        return IndicatorResult.unavailable(
                IndicatorStatus.MISSING_DATA, required, bars.size(), "no confirmed swing low exists");
    }

    public static IndicatorResult<SwingLow> latestSwingCandidate(List<QuantBar> input, int leftStrength) {
        var bars = completed(input);
        int required = leftStrength + 1;
        var invalid = Indicators.<SwingLow>validate(bars, required);
        if (invalid.isPresent()) return invalid.get();
        var candidate = bars.getLast();
        double low = candidate.low().doubleValue();
        boolean lower = bars.subList(bars.size() - required, bars.size() - 1).stream()
                .allMatch(bar -> low < bar.low().doubleValue());
        if (!lower) {
            return IndicatorResult.unavailable(
                    IndicatorStatus.MISSING_DATA, required, bars.size(), "latest bar is not a swing candidate");
        }
        return IndicatorResult.ready(
                new SwingLow(candidate.marketDate(), low, PivotStatus.CANDIDATE), required, bars.size());
    }

    public static IndicatorResult<EarningsGapStatistics> earningsGapStatistics(List<EarningsGap> gaps) {
        if (gaps.isEmpty()) {
            return IndicatorResult.unavailable(IndicatorStatus.WARMING_UP, 1, 0, "no earnings observations");
        }
        var percentages = gaps.stream()
                .mapToDouble(gap -> gap.open().subtract(gap.previousClose()).doubleValue()
                        / gap.previousClose().doubleValue())
                .toArray();
        double average = java.util.Arrays.stream(percentages).average().orElseThrow();
        double averageAbsolute =
                java.util.Arrays.stream(percentages).map(Math::abs).average().orElseThrow();
        long positive =
                java.util.Arrays.stream(percentages).filter(value -> value > 0).count();
        return IndicatorResult.ready(
                new EarningsGapStatistics(average, averageAbsolute, positive, gaps.size() - positive), 1, gaps.size());
    }

    private static List<QuantBar> completed(List<QuantBar> input) {
        return input.stream()
                .filter(QuantBar::completed)
                .sorted(Comparator.comparing(QuantBar::marketDate))
                .toList();
    }

    private static <T> Optional<IndicatorResult<T>> validate(List<QuantBar> bars, int required) {
        if (required < 1) {
            return Optional.of(IndicatorResult.unavailable(
                    IndicatorStatus.INVALID_DATA, required, bars.size(), "period must be positive"));
        }
        if (bars.size() < required) {
            return Optional.of(IndicatorResult.unavailable(
                    IndicatorStatus.WARMING_UP, required, bars.size(), "insufficient completed bars"));
        }
        for (int i = 1; i < bars.size(); i++) {
            LocalDate previous = bars.get(i - 1).marketDate();
            if (!bars.get(i).marketDate().isAfter(previous)) {
                return Optional.of(IndicatorResult.unavailable(
                        IndicatorStatus.INVALID_DATA, required, bars.size(), "duplicate market date"));
            }
        }
        return Optional.empty();
    }

    private static List<Double> closes(List<QuantBar> bars) {
        return bars.stream().map(bar -> bar.close().doubleValue()).toList();
    }

    private static double emaValue(List<Double> values, int period) {
        return emaSeries(values, period).getLast();
    }

    private static List<Double> emaSeries(List<Double> values, int period) {
        double multiplier = 2.0 / (period + 1.0);
        double current = values.subList(0, period).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        var result = new ArrayList<Double>();
        for (int i = 0; i < period - 1; i++) result.add(Double.NaN);
        result.add(current);
        for (int i = period; i < values.size(); i++) {
            current = ((values.get(i) - current) * multiplier) + current;
            result.add(current);
        }
        return result;
    }

    public record Macd(double line, double signal, double histogram) {}

    public enum PivotStatus {
        CANDIDATE,
        CONFIRMED
    }

    public record SwingLow(LocalDate marketDate, double price, PivotStatus status) {}

    public record EarningsGap(BigDecimal previousClose, BigDecimal open) {
        public EarningsGap {
            if (previousClose.signum() <= 0 || open.signum() <= 0) {
                throw new IllegalArgumentException("prices must be positive");
            }
        }
    }

    public record EarningsGapStatistics(
            double averageGap, double averageAbsoluteGap, long positiveCount, long nonPositiveCount) {}
}
