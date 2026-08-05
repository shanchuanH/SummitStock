package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.infrastructure.HoldingAnalysisStore;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationGenerationService {
    private final HoldingAnalysisApplicationService analysis;
    private final HoldingAnalysisStore store;
    private final Clock clock;

    public RecommendationGenerationService(
            HoldingAnalysisApplicationService analysis, HoldingAnalysisStore store, Clock clock) {
        this.analysis = analysis;
        this.store = store;
        this.clock = clock;
    }

    @Transactional
    public List<GeneratedRecommendation> generateAll(UUID userId) {
        var now = clock.instant();
        store.expireActive(userId);
        return analysis.analyzeAll(userId).stream()
                .map(value -> new GeneratedRecommendation(
                        store.appendRecommendation(
                                userId, value.snapshotId(), value.analysis(), value.resolution(), now),
                        value))
                .toList();
    }

    public record GeneratedRecommendation(
            UUID recommendationId, HoldingAnalysisApplicationService.AnalyzedHolding analyzedHolding) {}
}
