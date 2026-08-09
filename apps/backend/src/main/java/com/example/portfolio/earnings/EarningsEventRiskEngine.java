package com.example.portfolio.earnings;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;

public final class EarningsEventRiskEngine {
    public Risk assess(Input input) {
        if (!input.history().ready()) return Risk.MEDIUM;
        int score = moveScore(input.history().p75AbsMove());
        if (input.positionWeight().compareTo(new BigDecimal("0.10")) >= 0) score++;
        if (input.profitCushionR().compareTo(BigDecimal.ONE) < 0) score++;
        if (input.classification() == HoldingClassification.SPECULATIVE) score++;
        if (input.binaryEvent()) score++;
        return score >= 5 ? Risk.EXTREME : score >= 3 ? Risk.HIGH : score >= 1 ? Risk.MEDIUM : Risk.LOW;
    }

    private static int moveScore(BigDecimal p75) {
        if (p75 == null) return 1;
        if (p75.compareTo(new BigDecimal("0.12")) >= 0) return 3;
        if (p75.compareTo(new BigDecimal("0.07")) >= 0) return 2;
        if (p75.compareTo(new BigDecimal("0.04")) >= 0) return 1;
        return 0;
    }

    public enum Risk {
        LOW,
        MEDIUM,
        HIGH,
        EXTREME
    }

    public record Input(
            EarningsReactionStats.Summary history,
            BigDecimal positionWeight,
            BigDecimal profitCushionR,
            HoldingClassification classification,
            boolean binaryEvent) {}
}
