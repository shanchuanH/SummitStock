package com.example.portfolio.analysis.domain;

import java.util.List;

public record RecommendationCandidate(
        RecommendationAction action,
        DecisionChannel channel,
        EvidenceDependency evidenceDependency,
        String priority,
        int riskRank,
        String ruleId,
        String reason,
        List<String> risks) {
    public RecommendationCandidate {
        risks = List.copyOf(risks);
    }

    public RecommendationCandidate(
            RecommendationAction action,
            String priority,
            int riskRank,
            String ruleId,
            String reason,
            List<String> risks) {
        this(
                action,
                DecisionChannel.forAction(action),
                EvidenceDependency.forAction(action),
                priority,
                riskRank,
                ruleId,
                reason,
                risks);
    }
}
