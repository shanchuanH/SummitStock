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
        return calculate(input, Policy.defaults());
    }

    public static Result calculate(Input input, Policy policy) {
        requirePositive(input.entry(), "entry");
        requirePositive(input.atr(), "atr");
        if (isCoreEtf(input.classification())) {
            return new Result(
                    false, null, null, null, null, null, null, false, false, List.of(RuleIds.STOP_CORE_ETF_EXEMPT));
        }
        var structureStop = input.confirmedSwingLow().subtract(input.atr().multiply(policy.structureBufferAtr()));
        var k = input.volatilityAtrMultiplier() == null
                ? policy.multiplier(input.classification())
                : input.volatilityAtrMultiplier();
        requirePositive(k, "volatilityAtrMultiplier");
        var volatilityStop = input.entry().subtract(input.atr().multiply(k));
        var initialStop = structureStop.min(volatilityStop);
        var chandelier = input.chandelier() != null
                ? input.chandelier()
                : input.rollingHigh().subtract(input.atr().multiply(k));
        var liveStop = maximum(
                input.previousLiveStop(),
                chandelier,
                input.ema20().subtract(input.atr().multiply(policy.trailingEmaBufferAtr())),
                buffered(input.confirmedHigherLow(), input.atr(), policy.structureBufferAtr()),
                initialStop);
        var softAlert = liveStop.add(input.atr().multiply(policy.softAlertAtr()));
        var catastrophic = liveStop.subtract(input.atr().multiply(policy.catastrophicAtr()));
        var closeConfirmed = input.dailyClose().compareTo(liveStop) < 0;
        var rules = new java.util.ArrayList<String>();
        rules.add(RuleIds.STOP_INITIAL);
        rules.add(RuleIds.STOP_MONOTONIC);
        if (input.dailyClose().compareTo(softAlert) <= 0) rules.add(RuleIds.STOP_SOFT_ALERT);
        if (closeConfirmed) rules.add(RuleIds.STOP_CLOSE_CONFIRMED);
        return new Result(
                true,
                structureStop,
                volatilityStop,
                initialStop,
                liveStop,
                softAlert,
                catastrophic,
                closeConfirmed,
                false,
                rules);
    }

    private static boolean isCoreEtf(HoldingClassification classification) {
        return classification == HoldingClassification.CORE_BROAD_ETF
                || classification == HoldingClassification.CORE_TECH_ETF;
    }

    public record Policy(
            BigDecimal structureBufferAtr,
            BigDecimal qualityVolatilityAtr,
            BigDecimal tacticalVolatilityAtr,
            BigDecimal speculativeVolatilityAtr,
            BigDecimal trailingEmaBufferAtr,
            BigDecimal softAlertAtr,
            BigDecimal catastrophicAtr) {
        public BigDecimal multiplier(HoldingClassification classification) {
            return switch (classification) {
                case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> qualityVolatilityAtr;
                case SPECULATIVE -> speculativeVolatilityAtr;
                default -> tacticalVolatilityAtr;
            };
        }

        public static Policy defaults() {
            return new Policy(
                    QUARTER,
                    new BigDecimal("2.5"),
                    new BigDecimal("3.0"),
                    new BigDecimal("3.5"),
                    HALF,
                    HALF,
                    THREE_QUARTERS);
        }
    }

    private static BigDecimal maximum(BigDecimal... values) {
        BigDecimal result = null;
        for (var value : values) {
            if (value != null) result = result == null ? value : result.max(value);
        }
        if (result == null) throw new IllegalArgumentException("At least one stop candidate is required");
        return result;
    }

    private static BigDecimal buffered(BigDecimal value, BigDecimal atr, BigDecimal bufferAtr) {
        return value == null ? null : value.subtract(atr.multiply(bufferAtr));
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
            BigDecimal dailyClose,
            BigDecimal rollingHigh,
            BigDecimal volatilityAtrMultiplier) {
        public Input(
                HoldingClassification classification,
                BigDecimal entry,
                BigDecimal confirmedSwingLow,
                BigDecimal atr,
                BigDecimal previousLiveStop,
                BigDecimal chandelier,
                BigDecimal ema20,
                BigDecimal confirmedHigherLow,
                BigDecimal dailyClose) {
            this(
                    classification,
                    entry,
                    confirmedSwingLow,
                    atr,
                    previousLiveStop,
                    chandelier,
                    ema20,
                    confirmedHigherLow,
                    dailyClose,
                    null,
                    null);
        }
    }

    public record Result(
            boolean ordinaryStopApplicable,
            BigDecimal structureStop,
            BigDecimal volatilityStop,
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
