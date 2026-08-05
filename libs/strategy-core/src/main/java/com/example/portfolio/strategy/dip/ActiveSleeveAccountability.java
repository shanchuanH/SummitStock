package com.example.portfolio.strategy.dip;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import java.util.List;

public final class ActiveSleeveAccountability {
    private ActiveSleeveAccountability() {}

    public static Result review(int months, BigDecimal underperformance, boolean drawdownImproved) {
        if (underperformance.compareTo(new BigDecimal("0.05")) <= 0 || drawdownImproved)
            return new Result(BigDecimal.ONE, List.of());
        if (months >= 24) return new Result(new BigDecimal("0.50"), List.of(RuleIds.ACCOUNTABILITY_TWENTY_FOUR_MONTH));
        if (months >= 12) return new Result(new BigDecimal("0.75"), List.of(RuleIds.ACCOUNTABILITY_TWELVE_MONTH));
        return new Result(BigDecimal.ONE, List.of());
    }

    public record Result(BigDecimal budgetMultiplier, List<String> ruleIds) {}
}
