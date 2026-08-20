package com.example.portfolio.strategy.market;

import com.example.portfolio.strategy.RuleIds;
import java.util.ArrayList;
import java.util.List;

public final class MarketRegimeEngine {
    private MarketRegimeEngine() {}

    public static Result classify(Input input) {
        var rules = new ArrayList<String>();
        var narratives = new ArrayList<String>();
        if (input.quality() == EvidenceQuality.MISSING) {
            return new Result(
                    Label.MISSING_DATA,
                    0,
                    0,
                    0,
                    0,
                    0,
                    DecisionConfidence.WAIT_FOR_DATA,
                    false,
                    List.of("Required market evidence is missing; wait for data."),
                    List.of(RuleIds.DATA_MISSING_WAIT));
        }

        double trendScore = bounded(input.trend()) * 40;
        double momentumScore = bounded(input.momentum()) * 20;
        double breadthScore = bounded(input.breadth()) * 20;
        double stressScore = bounded(input.stressResilience()) * 20;
        double total = trendScore + momentumScore + breadthScore + stressScore;
        var label = label(total);

        if (input.spyBelow200Day() && input.qqqBelow200Day() && label.ordinal() > Label.YELLOW.ordinal()) {
            label = Label.YELLOW;
            rules.add(RuleIds.REGIME_BENCHMARK_TREND_CAP);
            narratives.add("SPY and QQQ are below their 200-day averages; regime is capped at YELLOW.");
        }
        if (input.vix() > 30 && input.breadth50() < 0.30) {
            label = Label.RED;
            rules.add(RuleIds.REGIME_PANIC_OVERRIDE);
            narratives.add("VIX above 30 and breadth below 30% triggered the panic override.");
        }
        boolean tacticalCap = input.qqqBelow200Day() && input.qqqMacdNegative() && input.qqqRsi() < 40;
        if (tacticalCap) {
            rules.add(RuleIds.REGIME_TACTICAL_CAP);
            narratives.add("QQQ trend, MACD, and RSI evidence caps tactical exposure at 5%.");
        }
        if (input.narrowRally()) {
            rules.add(RuleIds.REGIME_NARROW_RALLY);
            narratives.add("指数上涨集中在少数大型股，表面行情强于真实参与度。");
        }
        if (input.quality() == EvidenceQuality.STALE && label == Label.STRONG_GREEN) {
            label = Label.GREEN;
            rules.add(RuleIds.DATA_STALE_CAP);
            narratives.add("Stale evidence prevents a STRONG_GREEN classification.");
        }
        if (narratives.isEmpty()) narratives.add("Trend, momentum, breadth, and stress evidence are aligned.");
        return new Result(
                label,
                total,
                trendScore,
                momentumScore,
                breadthScore,
                stressScore,
                confidence(input.quality()),
                tacticalCap,
                narratives,
                rules);
    }

    private static Label label(double total) {
        if (total >= 85) return Label.STRONG_GREEN;
        if (total >= 70) return Label.GREEN;
        if (total >= 55) return Label.LIGHT_GREEN;
        if (total >= 40) return Label.YELLOW;
        if (total >= 25) return Label.ORANGE;
        return Label.RED;
    }

    private static DecisionConfidence confidence(EvidenceQuality quality) {
        return switch (quality) {
            case HEALTHY -> DecisionConfidence.HIGH;
            case PARTIAL -> DecisionConfidence.MEDIUM;
            case STALE, SUSPECT -> DecisionConfidence.LOW;
            case MISSING -> DecisionConfidence.WAIT_FOR_DATA;
        };
    }

    private static double bounded(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("component scores must be finite");
        return Math.clamp(value, 0, 1);
    }

    public enum Label {
        MISSING_DATA,
        RED,
        ORANGE,
        YELLOW,
        LIGHT_GREEN,
        GREEN,
        STRONG_GREEN
    }

    public record Input(
            double trend,
            double momentum,
            double breadth,
            double stressResilience,
            boolean spyBelow200Day,
            boolean qqqBelow200Day,
            double vix,
            double breadth50,
            boolean qqqMacdNegative,
            double qqqRsi,
            boolean narrowRally,
            EvidenceQuality quality) {}

    public record Result(
            Label label,
            double score,
            double trendScore,
            double momentumScore,
            double breadthScore,
            double stressScore,
            DecisionConfidence confidence,
            boolean tacticalCapFivePercent,
            List<String> narratives,
            List<String> ruleIds) {
        public Result {
            narratives = List.copyOf(narratives);
            ruleIds = List.copyOf(ruleIds);
        }
    }
}
