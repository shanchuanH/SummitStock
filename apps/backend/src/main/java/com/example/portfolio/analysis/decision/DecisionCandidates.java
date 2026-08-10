package com.example.portfolio.analysis.decision;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import java.util.List;

final class DecisionCandidates {
    private DecisionCandidates() {}

    static RecommendationCandidate of(
            RecommendationAction action, String priority, int rank, String rule, String reason, String risk) {
        return new RecommendationCandidate(action, priority, rank, rule, reason, List.of(risk));
    }
}
