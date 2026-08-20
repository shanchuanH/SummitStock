package com.example.portfolio.analysis.decision;

import static com.example.portfolio.analysis.decision.DecisionCandidates.of;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.strategy.RuleIds;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.SpeculativeTimeStopPolicy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class BehavioralFirewall {
    public List<RecommendationCandidate> evaluate(
            DecisionContext context, List<RecommendationCandidate> proposedCandidates) {
        var values = new ArrayList<RecommendationCandidate>();
        var strategy = context.evidence().strategy();
        boolean decisionCooling = context.lastDecisionAt() != null
                && !context.decisionAt().isBefore(context.lastDecisionAt())
                && Duration.between(context.lastDecisionAt(), context.decisionAt())
                                .compareTo(Duration.ofHours(strategy.coolingHours()))
                        < 0;
        boolean ideaCooling =
                context.ideaCooldownUntil() != null && context.decisionAt().isBefore(context.ideaCooldownUntil());
        if (decisionCooling || ideaCooling) {
            values.add(block(
                    RuleIds.RISK_COOLING_PERIOD, "The behavioral cooling period is active; new capital must wait."));
        }
        if (proposesNewCapital(proposedCandidates)
                && context.averagingDown()
                && (!context.thesisImproving()
                        || (context.evidence().position().classification() == HoldingClassification.SPECULATIVE
                                && !strategy.speculativeAverageDownAllowed()))) {
            values.add(block(
                    RuleIds.RISK_INVALID_AVERAGING,
                    "A lower price without new thesis evidence cannot justify averaging down."));
        }
        if (context.anchoredToCostBasis()) {
            values.add(block(
                    RuleIds.RISK_ANCHORING, "Cost basis and break-even reasoning are not valid decision anchors."));
        }
        if (context.evidence().position().classification() == HoldingClassification.SPECULATIVE
                && SpeculativeTimeStopPolicy.expired(
                        context.holdingTradingDays(),
                        strategy.speculativeTimeStopTradingDays(),
                        context.thesisProgress())) {
            values.add(of(
                    RecommendationAction.EXIT,
                    "MUST_ACT",
                    4,
                    RuleIds.SPECULATIVE_TIME_STOP,
                    "The speculative time stop expired without documented thesis progress.",
                    "Further waiting would replace the defined horizon with hope."));
        }
        return List.copyOf(values);
    }

    public static boolean allowsNewRisk(DecisionContext context) {
        var strategy = context.evidence().strategy();
        var decisionCooling = context.lastDecisionAt() != null
                && !context.decisionAt().isBefore(context.lastDecisionAt())
                && Duration.between(context.lastDecisionAt(), context.decisionAt())
                                .compareTo(Duration.ofHours(strategy.coolingHours()))
                        < 0;
        var ideaCooling =
                context.ideaCooldownUntil() != null && context.decisionAt().isBefore(context.ideaCooldownUntil());
        var invalidAverageDown = context.averagingDown() && !context.thesisImproving();
        return !decisionCooling && !ideaCooling && !invalidAverageDown && !context.anchoredToCostBasis();
    }

    private static boolean proposesNewCapital(List<RecommendationCandidate> candidates) {
        return candidates.stream()
                .anyMatch(candidate -> candidate.action() == RecommendationAction.ADD
                        || candidate.action() == RecommendationAction.STARTER_BUY);
    }

    private static RecommendationCandidate block(String ruleId, String reason) {
        return of(
                RecommendationAction.DO_NOT_ADD,
                "DO_NOT",
                2,
                ruleId,
                reason,
                "Behavioral discipline blocks new capital but never blocks risk reduction.");
    }
}
