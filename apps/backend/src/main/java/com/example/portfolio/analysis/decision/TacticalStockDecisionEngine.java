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
public final class TacticalStockDecisionEngine implements AssetDecisionEngine {
    @Override
    public Set<HoldingClassification> supports() {
        return Set.of(
                HoldingClassification.TACTICAL_STOCK,
                HoldingClassification.CYCLICAL_TACTICAL,
                HoldingClassification.TURNAROUND_TACTICAL);
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
                    "TACTICAL.EXIT",
                    "The tactical thesis or formal stop requires exit.",
                    "Waiting can exceed the planned risk."));
        }
        if (e.nextEvent().available()
                && ("HIGH".equals(e.nextEvent().eventRisk())
                        || "EXTREME".equals(e.nextEvent().eventRisk()))) {
            values.add(of(
                    RecommendationAction.REDUCE_HALF,
                    "MUST_ACT",
                    8,
                    "TACTICAL.EVENT.REDUCE",
                    "High event risk requires tactical exposure reduction.",
                    "A gap can bypass the formal stop."));
        }
        if (context.normalMax() != null
                && e.currentWeight().compareTo(context.normalMax()) < 0
                && "REVERSAL_CONFIRMED".equals(e.indicators().priceState())) {
            values.add(of(
                    RecommendationAction.ADD,
                    "NORMAL",
                    10,
                    "TACTICAL.CATALYST.CONFIRMED",
                    "Price reversal and tactical capacity permit an add.",
                    "Catalyst timing can fail."));
        }
        values.add(of(
                RecommendationAction.HOLD,
                "NORMAL",
                11,
                "TACTICAL.HOLD",
                "No tactical trigger requires action.",
                "Time, event, and stop evidence can change."));
        return List.copyOf(values);
    }
}
