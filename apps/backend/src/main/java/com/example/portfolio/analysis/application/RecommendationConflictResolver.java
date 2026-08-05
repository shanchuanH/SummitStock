package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.analysis.domain.RecommendationResolution;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class RecommendationConflictResolver {
    public RecommendationResolution resolve(List<RecommendationCandidate> candidates) {
        if (candidates.isEmpty())
            throw new IllegalArgumentException("At least one recommendation candidate is required");
        var ordered = candidates.stream()
                .sorted(Comparator.comparingInt(RecommendationCandidate::riskRank)
                        .thenComparing(RecommendationCandidate::ruleId))
                .toList();
        var winner = ordered.getFirst();
        var suppressed = ordered.subList(1, ordered.size());
        var reason = suppressed.isEmpty()
                ? winner.reason()
                : winner.reason() + " Suppressed " + suppressed.size() + " lower-priority candidate(s).";
        return new RecommendationResolution(winner, suppressed, reason);
    }
}
