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
public final class CoreEtfDecisionEngine implements AssetDecisionEngine {
    @Override
    public Set<HoldingClassification> supports() {
        return Set.of(HoldingClassification.CORE_BROAD_ETF, HoldingClassification.CORE_TECH_ETF);
    }

    @Override
    public List<RecommendationCandidate> evaluate(DecisionContext context) {
        var e = context.evidence();
        var sleeve = context.sleeveAllocation();
        var sleeveHasGap = sleeve != null
                && sleeve.gapWeight() != null
                && sleeve.gapWeight().signum() > 0;
        var values = new ArrayList<RecommendationCandidate>();
        if ("ETF_DIP_MARKET_DRIVEN".equals(e.drawdown().state())
                && e.emergencyCash().amount().compareTo(e.strategy().emergencyCashFloor()) >= 0
                && sleeveHasGap) {
            values.add(of(
                    RecommendationAction.DEPLOY_DIP_TRANCHE,
                    "NORMAL",
                    10,
                    "CORE_ETF.DIP.TRANCHE",
                    "Market-driven drawdown and protected reserve permit a qualified ETF dip tranche.",
                    "The tranche remains manual and setup confirmation can expire."));
        } else if (e.regime().available()
                && ("RED".equals(e.regime().label())
                        || "ORANGE".equals(e.regime().label()))) {
            values.add(of(
                    RecommendationAction.PAUSE_NEW_RISK,
                    "DO_NOT",
                    9,
                    "CORE_ETF.REGIME.PAUSE",
                    "Risk regime pauses ordinary core buying.",
                    "Weak market conditions can deepen before recovery."));
        } else if (sleeveHasGap && sleeve.primaryInstrumentPosition()) {
            values.add(of(
                    RecommendationAction.BUY,
                    "NORMAL",
                    10,
                    "CORE_ETF.ALLOCATION.ADD",
                    "The aggregate core sleeve is below target and this is its configured primary instrument.",
                    "Allocation changes remain manually executed."));
        }
        values.add(of(
                RecommendationAction.HOLD,
                "NORMAL",
                11,
                "CORE_ETF.HOLD",
                "Core allocation has no higher-priority action.",
                "Regime and reserve conditions can change."));
        return List.copyOf(values);
    }
}
