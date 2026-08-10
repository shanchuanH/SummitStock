package com.example.portfolio.analysis.domain;

import java.util.List;

public record RecommendationCandidate(
        RecommendationAction action, String priority, int riskRank, String ruleId, String reason, List<String> risks) {
    public RecommendationCandidate {
        risks = List.copyOf(risks);
    }
}
