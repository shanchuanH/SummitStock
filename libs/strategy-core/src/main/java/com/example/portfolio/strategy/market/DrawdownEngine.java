package com.example.portfolio.strategy.market;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;

public final class DrawdownEngine {
    private DrawdownEngine() {}

    public static Result classify(Input input) {
        if (input.currentEquity().signum() < 0 || input.previousHighWaterMark().signum() <= 0) {
            throw new IllegalArgumentException("equity and high-water mark must be valid");
        }
        if (!Double.isFinite(input.spyReturnFromPeak())
                || !Double.isFinite(input.qqqReturnFromPeak())
                || !Double.isFinite(input.breadth50())
                || !Double.isFinite(input.stressLevel())
                || !Double.isFinite(input.largestPositionContribution())
                || !Double.isFinite(input.largestClusterContribution())) {
            throw new IllegalArgumentException("drawdown evidence must be finite");
        }
        var highWaterMark = input.previousHighWaterMark().max(input.currentEquity());
        double drawdown = highWaterMark.signum() == 0
                ? 0
                : highWaterMark
                        .subtract(input.currentEquity())
                        .divide(highWaterMark, MathContext.DECIMAL64)
                        .doubleValue();
        var rules = new ArrayList<String>();
        var narratives = new ArrayList<String>();
        if (input.quality() == EvidenceQuality.MISSING) {
            return new Result(
                    highWaterMark,
                    drawdown,
                    State.WAIT_FOR_DATA,
                    Source.UNKNOWN,
                    false,
                    DecisionConfidence.WAIT_FOR_DATA,
                    List.of("Drawdown evidence is incomplete; wait for data."),
                    List.of(RuleIds.DATA_MISSING_WAIT));
        }
        if (drawdown >= 0.15
                && (input.quality() == EvidenceQuality.STALE || input.quality() == EvidenceQuality.SUSPECT)) {
            return new Result(
                    highWaterMark,
                    drawdown,
                    State.WAIT_FOR_DATA,
                    Source.UNKNOWN,
                    false,
                    DecisionConfidence.LOW,
                    List.of("Critical drawdown evidence is stale or suspect; precise source advice is blocked."),
                    List.of(RuleIds.DATA_STALE_CAP));
        }

        boolean benchmarkStress = Math.min(input.spyReturnFromPeak(), input.qqqReturnFromPeak()) <= -0.10;
        boolean broadStress = input.breadth50() < 0.40 || input.stressLevel() >= 0.65;
        boolean marketDriven = drawdown >= 0.15 && benchmarkStress && broadStress;
        boolean positionSpecific = input.largestPositionContribution() >= 0.50 && !benchmarkStress;
        State state = State.NORMAL;
        Source source = Source.MIXED;
        if (drawdown >= 0.20) {
            state = State.PAIN_LINE;
            rules.add(RuleIds.DRAWDOWN_PAIN_LINE);
            narratives.add("Portfolio drawdown reached the 20% Pain Line; no new risk is allowed.");
        } else if (drawdown >= 0.15) {
            if (marketDriven) {
                state = State.ETF_DIP_MARKET_DRIVEN;
                source = Source.MARKET_DRIVEN;
                rules.add(RuleIds.DRAWDOWN_MARKET_DRIVEN);
                narratives.add("Portfolio and benchmark stress confirm a market-driven drawdown.");
            } else {
                state = State.REVIEW_POSITION_SPECIFIC;
                source = positionSpecific ? Source.POSITION_SPECIFIC : Source.MIXED;
                rules.add(RuleIds.DRAWDOWN_POSITION_SPECIFIC);
                narratives.add(
                        "The 15% drawdown lacks sufficient broad-market confirmation; ETF Dip-Buy is not enabled.");
            }
        } else if (drawdown >= 0.12) {
            state = State.ETF_DIP_WATCH;
            rules.add(RuleIds.DRAWDOWN_ETF_WATCH);
            narratives.add("Portfolio drawdown reached ETF Dip Watch.");
        } else if (drawdown >= 0.10) {
            state = State.REDUCE_TACTICAL;
            rules.add(RuleIds.DRAWDOWN_REDUCE_TACTICAL);
            narratives.add("Portfolio drawdown requires tactical exposure review.");
        } else if (drawdown >= 0.08) {
            state = State.FREEZE_SPECULATION;
            rules.add(RuleIds.DRAWDOWN_FREEZE_SPECULATION);
            narratives.add("Portfolio drawdown freezes new speculative risk.");
        } else {
            narratives.add("Portfolio drawdown remains below the first 8% control threshold.");
        }
        if (source == Source.MIXED && positionSpecific) source = Source.POSITION_SPECIFIC;
        return new Result(
                highWaterMark, drawdown, state, source, marketDriven, confidence(input.quality()), narratives, rules);
    }

    private static DecisionConfidence confidence(EvidenceQuality quality) {
        return switch (quality) {
            case HEALTHY -> DecisionConfidence.HIGH;
            case PARTIAL -> DecisionConfidence.MEDIUM;
            case STALE, SUSPECT -> DecisionConfidence.LOW;
            case MISSING -> DecisionConfidence.WAIT_FOR_DATA;
        };
    }

    public enum State {
        NORMAL,
        FREEZE_SPECULATION,
        REDUCE_TACTICAL,
        ETF_DIP_WATCH,
        ETF_DIP_MARKET_DRIVEN,
        REVIEW_POSITION_SPECIFIC,
        PAIN_LINE,
        WAIT_FOR_DATA
    }

    public enum Source {
        MARKET_DRIVEN,
        POSITION_SPECIFIC,
        MIXED,
        UNKNOWN
    }

    public record Input(
            BigDecimal currentEquity,
            BigDecimal previousHighWaterMark,
            double spyReturnFromPeak,
            double qqqReturnFromPeak,
            double breadth50,
            double stressLevel,
            double largestPositionContribution,
            double largestClusterContribution,
            EvidenceQuality quality) {}

    public record Result(
            BigDecimal highWaterMark,
            double drawdown,
            State state,
            Source source,
            boolean marketDriven,
            DecisionConfidence confidence,
            List<String> narratives,
            List<String> ruleIds) {
        public Result {
            narratives = List.copyOf(narratives);
            ruleIds = List.copyOf(ruleIds);
        }
    }
}
