package com.example.portfolio.analysis.decision;

import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.util.List;
import java.util.Set;

public interface AssetDecisionEngine {
    Set<HoldingClassification> supports();

    List<RecommendationCandidate> evaluate(DecisionContext context);
}
