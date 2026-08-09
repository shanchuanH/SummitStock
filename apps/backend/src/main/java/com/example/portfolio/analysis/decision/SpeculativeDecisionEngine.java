package com.example.portfolio.analysis.decision;

import static com.example.portfolio.analysis.decision.DecisionCandidates.of;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class SpeculativeDecisionEngine implements AssetDecisionEngine {
    @Override
    public Set<HoldingClassification> supports() {
        return Set.of(HoldingClassification.SPECULATIVE);
    }

    @Override
    public List<RecommendationCandidate> evaluate(DecisionContext context) {
        var e = context.evidence();
        var values = new ArrayList<RecommendationCandidate>();
        if (e.stop().catastrophic() || e.stop().closeConfirmed() || e.thesis().invalidated()) {
            values.add(of(
                    RecommendationAction.EXIT,
                    "MUST_ACT",
                    e.thesis().invalidated() ? 4 : 5,
                    "SPECULATIVE.STOP.EXIT",
                    "Speculative risk has hit a thesis or formal stop.",
                    "There is no wait-for-recovery exception."));
        }
        if (e.nextEvent().available()
                && ("HIGH".equals(e.nextEvent().riskLevel())
                        || "EXTREME".equals(e.nextEvent().riskLevel()))) {
            values.add(of(
                    RecommendationAction.REDUCE_HALF,
                    "MUST_ACT",
                    8,
                    "SPECULATIVE.EVENT.REDUCE",
                    "High binary-event risk requires reduction.",
                    "Binary outcomes can gap beyond the risk budget."));
        }
        values.add(of(
                RecommendationAction.HOLD,
                "WATCH",
                11,
                "SPECULATIVE.WATCH",
                "No optimism-based add is permitted without a new validated setup.",
                "Time stop and risk budget remain binding."));
        return List.copyOf(values);
    }
}
