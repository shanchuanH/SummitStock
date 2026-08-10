package com.example.portfolio.analysis.decision;

import static com.example.portfolio.analysis.decision.DecisionCandidates.of;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class QualityStockDecisionEngine implements AssetDecisionEngine {
    @Override
    public Set<HoldingClassification> supports() {
        return Set.of(HoldingClassification.QUALITY_STOCK, HoldingClassification.QUALITY_GROWTH_HIGH_VOL);
    }

    @Override
    public List<RecommendationCandidate> evaluate(DecisionContext context) {
        var e = context.evidence();
        var values = new ArrayList<RecommendationCandidate>();
        var health = e.fundamentals().financialHealth();
        boolean broken = "BROKEN".equals(health);
        boolean structuralDeterioration =
                broken || "WEAKENING".equals(health) || e.thesis().invalidated();
        if (e.thesis().invalidated() || broken) {
            values.add(of(
                    RecommendationAction.EXIT,
                    "MUST_ACT",
                    4,
                    "QUALITY.OWNERSHIP.BROKEN",
                    "The ownership thesis or company health is broken.",
                    "Continuing to hold violates ownership quality."));
        }
        if (e.stop().catastrophic() || (e.stop().closeConfirmed() && structuralDeterioration)) {
            values.add(of(
                    RecommendationAction.EXIT,
                    "MUST_ACT",
                    5,
                    "QUALITY.STOP.CONFIRMED",
                    "A confirmed stop aligns with structural deterioration.",
                    "Delay can exceed the planned risk budget."));
        }
        if ("EXTREME".equals(e.nextEvent().eventRisk())
                && context.hardMax() != null
                && e.currentWeight().compareTo(context.hardMax()) >= 0) {
            values.add(of(
                    RecommendationAction.TRIM,
                    "MUST_ACT",
                    8,
                    "QUALITY.EVENT.OVERSIZED",
                    "Extreme event risk is paired with an oversized position.",
                    "Event gaps can bypass ordinary stop execution."));
        }
        if (context.normalMax() != null && e.currentWeight().compareTo(context.normalMax()) >= 0) {
            values.add(block("QUALITY.NORMAL_MAX", "Position is already at or above normal maximum weight."));
        }
        if ("STRONGLY_NEGATIVE".equals(e.fundamentals().estimateRevision())) {
            values.add(block("QUALITY.REVISION.NEGATIVE", "Forward revisions are strongly negative."));
        }
        if ("EXTREME".equals(e.valuation().state())) {
            values.add(block("QUALITY.VALUATION.EXTREME", "Valuation is extreme."));
        }
        if (context.readiness() != AnalysisReadiness.READY
                || !healthy(health)
                || e.fundamentals().estimateQuality()
                        == com.example.portfolio.strategy.market.EvidenceQuality.MISSING) {
            values.add(block("QUALITY.NEW_CAPITAL.DATA", "Evidence confidence is insufficient for new capital."));
        }
        if (healthy(health)
                && e.strategy().deepDiscountStarterEnabled()
                && !"STRONGLY_NEGATIVE".equals(e.fundamentals().estimateRevision())
                && "DEEP_DISCOUNT".equals(e.valuation().state())
                && belowNormal(context)) {
            values.add(of(
                    RecommendationAction.STARTER_BUY,
                    "NORMAL",
                    10,
                    "QUALITY.DEEP_DISCOUNT.STARTER",
                    "Healthy ownership evidence and deep discount permit a limited starter.",
                    "Weak trend limits sizing and later adds require confirmation."));
        }
        if (healthy(health)
                && acceptableValuation(e.valuation().state())
                && atLeastFlat(e.fundamentals().estimateRevision())
                && !"DEEP_DISCOUNT".equals(e.valuation().state())
                && confirmedPrice(e.indicators().priceState())
                && belowNormal(context)) {
            values.add(of(
                    RecommendationAction.ADD,
                    "NORMAL",
                    10,
                    "QUALITY.CONFIRMED.ADD",
                    "Health, valuation, revisions, price confirmation, and capacity permit an add.",
                    "Evidence can change before manual execution."));
        }
        values.add(hold());
        return List.copyOf(values);
    }

    private static RecommendationCandidate block(String rule, String reason) {
        return of(
                RecommendationAction.DO_NOT_ADD,
                "DO_NOT",
                9,
                rule,
                reason,
                "New capital lacks a required quality gate.");
    }

    private static RecommendationCandidate hold() {
        return of(
                RecommendationAction.HOLD,
                "NORMAL",
                11,
                "QUALITY.HOLD",
                "No higher-priority quality rule requires action.",
                "Future evidence can change the conclusion.");
    }

    private static boolean belowNormal(DecisionContext c) {
        return c.normalMax() != null && c.evidence().currentWeight().compareTo(c.normalMax()) < 0;
    }

    private static boolean healthy(String v) {
        return "HEALTHY".equals(v) || "STRONG".equals(v);
    }

    private static boolean acceptableValuation(String v) {
        return "DEEP_DISCOUNT".equals(v) || "ATTRACTIVE".equals(v) || "FAIR".equals(v);
    }

    private static boolean atLeastFlat(String v) {
        return "FLAT".equals(v) || "POSITIVE".equals(v) || "STRONGLY_POSITIVE".equals(v);
    }

    private static boolean confirmedPrice(String v) {
        return "UPTREND".equals(v) || "STRONG_UPTREND".equals(v) || "REVERSAL_CONFIRMED".equals(v);
    }
}
