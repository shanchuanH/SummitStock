package com.example.portfolio.strategy.position;

import com.example.portfolio.strategy.RuleIds;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.util.List;

public final class StopEngine {
    private static final BigDecimal QUARTER = new BigDecimal("0.25");
    private static final BigDecimal HALF = new BigDecimal("0.5");
    private static final BigDecimal THREE_QUARTERS = new BigDecimal("0.75");

    private StopEngine() {}

    public static Result calculate(Input input) {
        requirePositive(input.entry(), "entry");
        requirePositive(input.atr(), "atr");
        if (isCoreEtf(input.classification())) {
            return new Result(false, null, null, null, null, false, false, List.of(RuleIds.STOP_CORE_ETF_EXEMPT));
        }
        var structureStop = input.confirmedSwingLow().subtract(input.atr().multiply(QUARTER));
        var volatilityStop = input.entry().subtract(input.atr().multiply(multiplier(input.classification())));
        var initialStop = structureStop.min(volatilityStop);
        var liveStop = maximum(
                input.previousLiveStop(),
                input.chandelier(),
                input.ema20().subtract(input.atr().multiply(HALF)),
                input.confirmedHigherLow().subtract(input.atr().multiply(QUARTER)),
                initialStop);
        var softAlert = liveStop.add(input.atr().multiply(HALF));
        var catastrophic = liveStop.subtract(input.atr().multiply(THREE_QUARTERS));
        var closeConfirmed = input.dailyClose().compareTo(liveStop) < 0;
        var rules = new java.util.ArrayList<String>();
        rules.add(RuleIds.STOP_INITIAL);
        rules.add(RuleIds.STOP_MONOTONIC);
        if (input.dailyClose().compareTo(softAlert) <= 0) rules.add(RuleIds.STOP_SOFT_ALERT);
        if (closeConfirmed) rules.add(RuleIds.STOP_CLOSE_CONFIRMED);
        return new Result(true, initialStop, liveStop, softAlert, catastrophic, closeConfirmed, false, rules);
    }

    private static boolean isCoreEtf(HoldingClassification classification) {
        return classification == HoldingClassification.CORE_BROAD_ETF
                || classification == HoldingClassification.CORE_TECH_ETF;
    }

    private static BigDecimal multiplier(HoldingClassification classification) {
        return switch (classification) {
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> new BigDecimal("2.5");
            case SPECULATIVE -> new BigDecimal("3.5");
            default -> new BigDecimal("3.0");
        };
    }

    private static BigDecimal maximum(BigDecimal... values) {
        var result = values[0];
        for (var value : values) result = result.max(value);
        return result;
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(name + " must be positive");
    }

    public record Input(
            HoldingClassification classification,
            BigDecimal entry,
            BigDecimal confirmedSwingLow,
            BigDecimal atr,
            BigDecimal previousLiveStop,
            BigDecimal chandelier,
            BigDecimal ema20,
            BigDecimal confirmedHigherLow,
            BigDecimal dailyClose) {}

    public record Result(
            boolean ordinaryStopApplicable,
            BigDecimal initialStop,
            BigDecimal liveStop,
            BigDecimal softAlert,
            BigDecimal catastrophicStop,
            boolean closeConfirmed,
            boolean catastrophicBreach,
            List<String> ruleIds) {
        public Result {
            ruleIds = List.copyOf(ruleIds);
        }
    }
}
