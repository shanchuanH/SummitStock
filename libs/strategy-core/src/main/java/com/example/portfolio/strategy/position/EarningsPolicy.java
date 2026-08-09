package com.example.portfolio.strategy.position;

import com.example.portfolio.strategy.RuleIds;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.util.List;

public final class EarningsPolicy {
    private EarningsPolicy() {}

    public static Result review(Input input) {
        if (input.classification() == HoldingClassification.THEMATIC_ETF) {
            return new Result("NOT_APPLICABLE", List.of());
        }
        if (input.eventCount() < 8 || input.eventCount() > 12) return new Result("WAIT_FOR_DATA", List.of());
        return switch (input.classification()) {
            case SPECULATIVE ->
                input.binaryEvent()
                        ? new Result("EXIT_BEFORE_EVENT", List.of(RuleIds.EARNINGS_SPECULATIVE_EXIT))
                        : input.profitCushionR().compareTo(BigDecimal.ONE) < 0
                                ? new Result("REDUCE_BEFORE_EVENT", List.of(RuleIds.EARNINGS_SPECULATIVE_EXIT))
                                : new Result("HOLD_WITH_REVIEW", List.of(RuleIds.EARNINGS_QUALITY_REVIEW));
            case TACTICAL_STOCK, CYCLICAL_TACTICAL, TURNAROUND_TACTICAL ->
                input.profitCushionR().compareTo(BigDecimal.ONE) < 0 && high(input.eventRisk())
                        ? new Result("REDUCE_HALF", List.of(RuleIds.EARNINGS_TACTICAL_REDUCE))
                        : new Result("HOLD_WITH_REVIEW", List.of(RuleIds.EARNINGS_QUALITY_REVIEW));
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL ->
                input.overRiskLimit()
                        ? new Result("TRIM_TO_RISK_LIMIT", List.of(RuleIds.EARNINGS_QUALITY_REVIEW))
                        : new Result("HOLD_THROUGH_EVENT", List.of(RuleIds.EARNINGS_QUALITY_REVIEW));
            default -> new Result("REVIEW", List.of(RuleIds.EARNINGS_QUALITY_REVIEW));
        };
    }

    private static boolean high(EventRisk risk) {
        return risk == EventRisk.HIGH || risk == EventRisk.EXTREME;
    }

    public enum EventRisk {
        LOW,
        MEDIUM,
        HIGH,
        EXTREME
    }

    public record Input(
            HoldingClassification classification,
            int eventCount,
            BigDecimal profitCushionR,
            boolean overRiskLimit,
            EventRisk eventRisk,
            boolean binaryEvent) {
        public Input(
                HoldingClassification classification,
                int eventCount,
                BigDecimal profitCushionR,
                boolean overRiskLimit) {
            this(classification, eventCount, profitCushionR, overRiskLimit, EventRisk.HIGH, true);
        }
    }

    public record Result(String action, List<String> ruleIds) {}
}
