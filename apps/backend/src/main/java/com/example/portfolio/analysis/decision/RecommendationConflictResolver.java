package com.example.portfolio.analysis.decision;

import com.example.portfolio.analysis.domain.DecisionChannel;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.analysis.domain.RecommendationResolution;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class RecommendationConflictResolver {
    public RecommendationResolution resolve(List<RecommendationCandidate> candidates) {
        return resolve(candidates, true);
    }

    public RecommendationResolution resolve(List<RecommendationCandidate> candidates, boolean riskPriorityOverTax) {
        if (candidates.isEmpty()) throw new IllegalArgumentException("At least one candidate is required");
        var reductions = candidates.stream()
                .filter(candidate -> candidate.channel() == DecisionChannel.RISK_REDUCTION)
                .sorted(Comparator.comparingInt(RecommendationConflictResolver::reductionStrength)
                        .thenComparingInt(RecommendationCandidate::riskRank)
                        .thenComparing(RecommendationCandidate::ruleId))
                .toList();
        var eligible = reductions.isEmpty() || !riskPriorityOverTax ? candidates : reductions;
        var ordered = eligible.stream()
                .sorted(Comparator.comparingInt(RecommendationCandidate::riskRank)
                        .thenComparing(RecommendationCandidate::ruleId))
                .toList();
        var winner = reductions.isEmpty() || !riskPriorityOverTax ? ordered.getFirst() : reductions.getFirst();
        var suppressed =
                candidates.stream().filter(candidate -> candidate != winner).toList();
        var reason = suppressed.isEmpty()
                ? winner.reason()
                : winner.reason() + " Suppressed " + suppressed.size() + " lower-priority candidate(s).";
        return new RecommendationResolution(winner, suppressed, reason);
    }

    private static int reductionStrength(RecommendationCandidate candidate) {
        return switch (candidate.action()) {
            case EXIT -> 0;
            case REDUCE_HALF -> 1;
            case TRIM -> 2;
            default -> throw new IllegalArgumentException("Not a risk-reduction action: " + candidate.action());
        };
    }
}
