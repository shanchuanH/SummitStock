package com.example.portfolio.analysis.decision;

import static com.example.portfolio.analysis.decision.DecisionCandidates.of;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class AssetDecisionRouter {
    private final Map<HoldingClassification, AssetDecisionEngine> engines;

    public AssetDecisionRouter(List<AssetDecisionEngine> engines) {
        var values = new java.util.EnumMap<HoldingClassification, AssetDecisionEngine>(HoldingClassification.class);
        for (var engine : engines) {
            for (var classification : engine.supports()) {
                if (values.put(classification, engine) != null) {
                    throw new IllegalStateException("Duplicate decision engine for " + classification);
                }
            }
        }
        this.engines = Map.copyOf(values);
    }

    public List<RecommendationCandidate> evaluate(DecisionContext context) {
        var engine = engines.get(context.evidence().position().classification());
        return engine == null
                ? List.of(of(
                        RecommendationAction.WAIT_FOR_DATA,
                        "DO_NOT",
                        1,
                        "ASSET.ENGINE.MISSING",
                        "No decision engine supports this classification.",
                        "No action is permitted without an asset policy."))
                : engine.evaluate(context);
    }
}
