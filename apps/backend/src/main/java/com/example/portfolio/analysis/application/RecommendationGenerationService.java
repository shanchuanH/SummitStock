package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.infrastructure.HoldingAnalysisStore;
import com.example.portfolio.analysis.narrative.AnalystNarrationService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationGenerationService {
    private final HoldingAnalysisApplicationService analysis;
    private final HoldingAnalysisStore store;
    private final AnalystNarrationService narration;
    private final Clock clock;

    public RecommendationGenerationService(
            HoldingAnalysisApplicationService analysis,
            HoldingAnalysisStore store,
            AnalystNarrationService narration,
            Clock clock) {
        this.analysis = analysis;
        this.store = store;
        this.narration = narration;
        this.clock = clock;
    }

    @Transactional
    public List<GeneratedRecommendation> generateAll(UUID userId) {
        var now = clock.instant();
        store.expireActive(userId);
        return analysis.analyzeAll(userId).stream()
                .map(value -> generate(userId, value, now))
                .toList();
    }

    private GeneratedRecommendation generate(
            UUID userId, HoldingAnalysisApplicationService.AnalyzedHolding value, java.time.Instant now) {
        var recommendationId =
                store.appendRecommendation(userId, value.snapshotId(), value.analysis(), value.resolution(), now);
        narration.generate(recommendationId, value);
        return new GeneratedRecommendation(recommendationId, value);
    }

    public record GeneratedRecommendation(
            UUID recommendationId, HoldingAnalysisApplicationService.AnalyzedHolding analyzedHolding) {}
}
