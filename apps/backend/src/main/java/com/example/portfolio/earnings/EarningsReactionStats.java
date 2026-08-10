package com.example.portfolio.earnings;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class EarningsReactionStats {
    private static final MathContext MATH = MathContext.DECIMAL128;

    public Summary summarize(List<Reaction> values) {
        var recent = values.stream().limit(12).toList();
        if (recent.size() < 8) return Summary.missing(recent.size());
        var absoluteMoves = recent.stream()
                .map(Reaction::return1d)
                .filter(java.util.Objects::nonNull)
                .map(BigDecimal::abs)
                .sorted()
                .toList();
        var gaps = recent.stream()
                .map(Reaction::gapReturn)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        var preRunups = recent.stream()
                .map(Reaction::preEvent20dReturn)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList();
        if (absoluteMoves.isEmpty() || gaps.isEmpty() || preRunups.isEmpty()) return Summary.missing(recent.size());
        return new Summary(
                recent.size(),
                percentile(absoluteMoves, new BigDecimal("0.50")),
                percentile(absoluteMoves, new BigDecimal("0.75")),
                percentile(absoluteMoves, new BigDecimal("0.90")),
                gaps.getFirst(),
                gaps.getLast(),
                percentile(preRunups, new BigDecimal("0.50")));
    }

    private static BigDecimal percentile(List<BigDecimal> sorted, BigDecimal probability) {
        var clean = new ArrayList<>(
                sorted.stream().filter(java.util.Objects::nonNull).toList());
        clean.sort(Comparator.naturalOrder());
        if (clean.isEmpty()) return null;
        int rank = probability
                .multiply(BigDecimal.valueOf(clean.size()), MATH)
                .setScale(0, java.math.RoundingMode.CEILING)
                .intValue();
        return clean.get(Math.max(0, rank - 1));
    }

    public record Reaction(BigDecimal return1d, BigDecimal gapReturn, BigDecimal preEvent20dReturn) {}

    public record Summary(
            int eventCount,
            BigDecimal medianAbsMove,
            BigDecimal p75AbsMove,
            BigDecimal p90AbsMove,
            BigDecimal worstDownsideGap,
            BigDecimal bestUpsideGap,
            BigDecimal medianPreRunup) {
        static Summary missing(int count) {
            return new Summary(count, null, null, null, null, null, null);
        }

        public boolean ready() {
            return eventCount >= 8;
        }
    }
}
