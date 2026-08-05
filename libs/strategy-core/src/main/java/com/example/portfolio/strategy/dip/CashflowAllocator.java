package com.example.portfolio.strategy.dip;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import java.util.List;

public final class CashflowAllocator {
    private static final BigDecimal FLOOR = new BigDecimal("20000");

    private CashflowAllocator() {}

    public static Plan allocate(
            BigDecimal takeHome, BigDecimal expenses, BigDecimal emergencyCash, boolean qualitySignal) {
        var surplus = takeHome.subtract(expenses).max(BigDecimal.ZERO);
        var emergency = FLOOR.subtract(emergencyCash).max(BigDecimal.ZERO).min(surplus);
        var remaining = surplus.subtract(emergency);
        if (emergency.signum() > 0)
            return new Plan(
                    surplus,
                    emergency,
                    remaining,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    List.of(RuleIds.CASHFLOW_EMERGENCY_FIRST));
        var broad = remaining.multiply(new BigDecimal(qualitySignal ? "0.50" : "0.60"));
        var quality = qualitySignal ? remaining.multiply(new BigDecimal("0.10")) : BigDecimal.ZERO;
        return new Plan(
                surplus,
                emergency,
                broad,
                remaining.multiply(new BigDecimal("0.15")),
                remaining.multiply(new BigDecimal("0.10")),
                remaining.multiply(new BigDecimal("0.15")),
                quality,
                qualitySignal ? List.of() : List.of(RuleIds.CASHFLOW_NO_SIGNAL_FALLBACK));
    }

    public record Plan(
            BigDecimal surplus,
            BigDecimal emergency,
            BigDecimal broadCore,
            BigDecimal techCore,
            BigDecimal internationalCore,
            BigDecimal tacticalReserve,
            BigDecimal qualityOpportunity,
            List<String> ruleIds) {}
}
