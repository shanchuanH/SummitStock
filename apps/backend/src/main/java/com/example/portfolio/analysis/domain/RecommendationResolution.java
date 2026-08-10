package com.example.portfolio.analysis.domain;

import java.util.List;

public record RecommendationResolution(
        RecommendationCandidate winner, List<RecommendationCandidate> suppressed, String reason) {
    public RecommendationResolution {
        suppressed = List.copyOf(suppressed);
    }
}
