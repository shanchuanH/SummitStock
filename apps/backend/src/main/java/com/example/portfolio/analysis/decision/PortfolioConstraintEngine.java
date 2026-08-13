package com.example.portfolio.analysis.decision;

import static com.example.portfolio.analysis.decision.DecisionCandidates.of;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class PortfolioConstraintEngine {
    public List<RecommendationCandidate> evaluate(DecisionContext context) {
        var evidence = context.evidence();
        var values = new ArrayList<RecommendationCandidate>();
        if (context.readiness() != AnalysisReadiness.READY && context.readiness() != AnalysisReadiness.PARTIAL) {
            values.add(of(
                    RecommendationAction.WAIT_FOR_DATA,
                    "DO_NOT",
                    1,
                    "DECISION.DATA.ELIGIBILITY",
                    "Required evidence is not actionable: " + context.readiness() + ".",
                    "Incomplete evidence can create false precision."));
        }
        if (!evidence.position().classificationConfirmed()
                || evidence.position().classification() == HoldingClassification.UNKNOWN) {
            values.add(of(
                    RecommendationAction.WAIT_FOR_DATA,
                    "DO_NOT",
                    1,
                    "DECISION.CLASSIFICATION.REQUIRED",
                    "The asset classification must be confirmed before applying strategy rules.",
                    "The wrong classification applies the wrong ownership and risk policy."));
        }
        if (evidence.emergencyCash().amount().compareTo(evidence.strategy().emergencyCashFloor()) < 0) {
            values.add(of(
                    RecommendationAction.PAUSE_NEW_RISK,
                    "DO_NOT",
                    2,
                    "PORTFOLIO.EMERGENCY.RESERVE",
                    "Emergency reserve is below the protected floor.",
                    "New capital would impair emergency liquidity."));
        }
        if (evidence.drawdown().noNewRisk()) {
            values.add(of(
                    RecommendationAction.PAUSE_NEW_RISK,
                    "DO_NOT",
                    3,
                    "PORTFOLIO.PAIN_LINE",
                    "Portfolio drawdown reached the configured pain line.",
                    "Adding risk during the pain-line state violates portfolio controls."));
        }
        if (context.hardMax() != null && evidence.currentWeight().compareTo(context.hardMax()) > 0) {
            values.add(of(
                    RecommendationAction.TRIM,
                    "MUST_ACT",
                    6,
                    "POSITION.HARD_CAP",
                    "Position weight exceeds the hard cap.",
                    "Concentration is above the strategy limit."));
        }
        if (evidence.clusterOpenRisk() == null
                || evidence.totalOpenRisk() == null
                || evidence.clusterOpenRisk().compareTo(evidence.strategy().clusterOpenRiskMax()) >= 0
                || evidence.totalOpenRisk().compareTo(evidence.strategy().totalOpenRiskMax()) >= 0) {
            values.add(of(
                    RecommendationAction.DO_NOT_ADD,
                    "DO_NOT",
                    7,
                    "PORTFOLIO.OPEN_RISK.CAP",
                    evidence.clusterOpenRisk() == null || evidence.totalOpenRisk() == null
                            ? "Cluster or total open-risk capacity is not available."
                            : "Cluster or total open-risk capacity is exhausted.",
                    "Correlated holdings can lose together."));
        }
        return List.copyOf(values);
    }
}
