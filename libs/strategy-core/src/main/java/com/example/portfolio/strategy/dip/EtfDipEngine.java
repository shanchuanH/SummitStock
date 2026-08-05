package com.example.portfolio.strategy.dip;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public final class EtfDipEngine {
    private static final List<BigDecimal> TRANCHES =
            List.of(new BigDecimal("0.20"), new BigDecimal("0.25"), new BigDecimal("0.30"), new BigDecimal("0.25"));

    private EtfDipEngine() {}

    public static Result evaluate(Input input) {
        var rules = new ArrayList<String>();
        var score = input.drawdownScore() * 30
                + input.vixPercentileScore() * 20
                + input.breadthOversoldScore() * 20
                + input.creditStressScore() * 15
                + input.volTermScore() * 10
                + input.trendContextScore() * 5;
        if (!input.completeData())
            return new Result("WAIT_FOR_DATA", score, 0, null, null, List.of(RuleIds.DIP_DATA_COMPLETE));
        if (input.portfolioDrawdown().compareTo(new BigDecimal("0.20")) >= 0)
            return new Result(
                    "PAIN_LINE_NO_NEW_RISK", score, triggerCount(input), null, null, List.of(RuleIds.DIP_PAIN_LINE));
        if (!input.marketDriven()
                || input.portfolioDrawdown().compareTo(new BigDecimal("0.15")) < 0
                || !input.emergencyCashProtected())
            return new Result("WATCH", score, triggerCount(input), null, null, List.of(RuleIds.DIP_MARKET_DRIVEN));
        if (score < 60)
            return new Result(
                    "WAIT_FOR_SETUP", score, triggerCount(input), null, null, List.of(RuleIds.DIP_SETUP_SCORE));
        var triggers = triggerCount(input);
        if (triggers < 2)
            return new Result("WAIT_FOR_CONFIRMATION", score, triggers, null, null, List.of(RuleIds.DIP_TRIGGER_COUNT));
        int next = input.completedTranches() + 1;
        if (next > 4)
            return new Result(
                    "HOLD_CORE_RECOVERY_TACTICAL_ONLY",
                    score,
                    triggers,
                    null,
                    null,
                    List.of(RuleIds.DIP_RECOVERY_TACTICAL_ONLY));
        if (input.completedTranches() > 0 && input.tradingDaysSinceLastTranche() < 5) rules.add(RuleIds.DIP_COOLDOWN);
        if (!rules.isEmpty()) return new Result("COOLDOWN", score, triggers, next, null, rules);
        rules.add(RuleIds.DIP_TRANCHE_UNIQUE);
        rules.add(RuleIds.RECOMMENDATION_MANUAL_ONLY);
        return new Result("DEPLOY_TRANCHE", score, triggers, next, TRANCHES.get(next - 1), rules);
    }

    private static int triggerCount(Input input) {
        return (input.rsiCross40() ? 1 : 0)
                + (input.breakout5Day() ? 1 : 0)
                + (input.aboveEma20() ? 1 : 0)
                + (input.breadthImproving() ? 1 : 0)
                + (input.vixFalling() ? 1 : 0)
                + (input.creditStable() ? 1 : 0);
    }

    public record Input(
            BigDecimal portfolioDrawdown,
            boolean marketDriven,
            boolean completeData,
            boolean emergencyCashProtected,
            double drawdownScore,
            double vixPercentileScore,
            double breadthOversoldScore,
            double creditStressScore,
            double volTermScore,
            double trendContextScore,
            boolean rsiCross40,
            boolean breakout5Day,
            boolean aboveEma20,
            boolean breadthImproving,
            boolean vixFalling,
            boolean creditStable,
            int completedTranches,
            int tradingDaysSinceLastTranche) {}

    public record Result(
            String action,
            double setupScore,
            int triggerCount,
            Integer trancheNumber,
            BigDecimal reserveFraction,
            List<String> ruleIds) {}
}
