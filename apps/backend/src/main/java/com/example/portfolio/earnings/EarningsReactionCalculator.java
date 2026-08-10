package com.example.portfolio.earnings;

import com.example.portfolio.market.provider.TradingCalendar;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.List;

public final class EarningsReactionCalculator {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private final TradingCalendar calendar;

    public EarningsReactionCalculator(TradingCalendar calendar) {
        this.calendar = calendar;
    }

    public Result calculate(LocalDate eventDate, EarningsCalendarProvider.Timing timing, List<Bar> bars) {
        var effectiveSession = calendar.isSession(eventDate) ? eventDate : calendar.nextSession(eventDate);
        var ordered = bars.stream()
                .filter(bar -> calendar.isSession(bar.date()))
                .sorted(java.util.Comparator.comparing(Bar::date))
                .toList();
        var before = ordered.stream()
                .filter(bar -> timing == EarningsCalendarProvider.Timing.BEFORE_OPEN
                        ? bar.date().isBefore(effectiveSession)
                        : !bar.date().isAfter(effectiveSession))
                .toList();
        var firstReactionSession = timing == EarningsCalendarProvider.Timing.BEFORE_OPEN
                ? effectiveSession
                : calendar.nextSession(effectiveSession);
        var after = ordered.stream()
                .filter(bar -> !bar.date().isBefore(firstReactionSession))
                .toList();
        if (before.isEmpty() || after.isEmpty()) return null;
        var pre = before.getLast();
        var next = after.getFirst();
        var priorVolumes = before.stream()
                .skip(Math.max(0, before.size() - 20L))
                .map(Bar::volume)
                .sorted()
                .toList();
        var medianVolume = priorVolumes.isEmpty() ? null : priorVolumes.get(priorVolumes.size() / 2);
        var pre20 = before.get(Math.max(0, before.size() - 20));
        return new Result(
                pre.close(),
                next.open(),
                next.close(),
                change(next.close(), pre.close()),
                after.size() >= 3 ? change(after.get(2).close(), pre.close()) : null,
                after.size() >= 5 ? change(after.get(4).close(), pre.close()) : null,
                change(next.open(), pre.close()),
                ratio(next.volume(), medianVolume),
                change(pre.close(), pre20.close()));
    }

    private static BigDecimal change(BigDecimal value, BigDecimal base) {
        var ratio = ratio(value, base);
        return ratio == null ? null : ratio.subtract(BigDecimal.ONE);
    }

    private static BigDecimal ratio(BigDecimal value, BigDecimal base) {
        return value == null || base == null || base.signum() == 0 ? null : value.divide(base, MATH);
    }

    public record Bar(LocalDate date, BigDecimal open, BigDecimal close, BigDecimal volume) {}

    public record Result(
            BigDecimal preClose,
            BigDecimal nextOpen,
            BigDecimal nextClose,
            BigDecimal return1d,
            BigDecimal return3d,
            BigDecimal return5d,
            BigDecimal gapReturn,
            BigDecimal volumeShock,
            BigDecimal preEvent20dReturn) {}
}
