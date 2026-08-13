package com.example.portfolio.strategy.dip;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import java.util.List;

public final class CashflowAllocator {
    private static final BigDecimal FLOOR = new BigDecimal("20000");

    private CashflowAllocator() {}

    public static Plan allocate(
            BigDecimal takeHome, BigDecimal expenses, BigDecimal emergencyCash, boolean qualitySignal) {
        return allocate(takeHome, expenses, emergencyCash, qualitySignal, Policy.defaults());
    }

    public static Plan allocate(
            BigDecimal takeHome, BigDecimal expenses, BigDecimal emergencyCash, boolean qualitySignal, Policy policy) {
        var surplus = takeHome.subtract(expenses).max(BigDecimal.ZERO);
        var emergency = policy.emergencyFloor()
                .subtract(emergencyCash)
                .max(BigDecimal.ZERO)
                .min(surplus);
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
        var broad = remaining.multiply(qualitySignal ? policy.broadWithSignal() : policy.broadWithoutSignal());
        var quality = qualitySignal ? remaining.multiply(policy.qualityWithSignal()) : BigDecimal.ZERO;
        return new Plan(
                surplus,
                emergency,
                broad,
                remaining.multiply(policy.tech()),
                remaining.multiply(policy.international()),
                remaining.multiply(policy.tacticalReserve()),
                quality,
                qualitySignal ? List.of() : List.of(RuleIds.CASHFLOW_NO_SIGNAL_FALLBACK));
    }

    public record Policy(
            BigDecimal emergencyFloor,
            BigDecimal broadWithoutSignal,
            BigDecimal broadWithSignal,
            BigDecimal tech,
            BigDecimal international,
            BigDecimal tacticalReserve,
            BigDecimal qualityWithSignal) {
        public static Policy defaults() {
            return new Policy(
                    FLOOR,
                    new BigDecimal("0.60"),
                    new BigDecimal("0.50"),
                    new BigDecimal("0.15"),
                    new BigDecimal("0.10"),
                    new BigDecimal("0.15"),
                    new BigDecimal("0.10"));
        }
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
