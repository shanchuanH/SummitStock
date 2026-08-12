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
    private final PublishedStrategyService strategies;
    private final Clock clock;

    public RecommendationGenerationService(
            HoldingAnalysisApplicationService analysis,
            HoldingAnalysisStore store,
            AnalystNarrationService narration,
            PublishedStrategyService strategies,
            Clock clock) {
        this.analysis = analysis;
        this.store = store;
        this.narration = narration;
        this.strategies = strategies;
        this.clock = clock;
    }

    @Transactional
    public List<GeneratedRecommendation> generateAll(UUID userId) {
        strategies.requireFormalRecommendationStrategy();
        return generate(userId, analysis.analyzeAll(userId));
    }

    @Transactional
    public List<GeneratedRecommendation> generateForRun(UUID userId, UUID analysisRunId) {
        strategies.requireFormalRecommendationStrategy();
        var persisted = store.forRun(userId, analysisRunId);
        if (persisted.isEmpty()) {
            throw new IllegalStateException("Analysis run has no persisted holding snapshots");
        }
        var now = clock.instant();
        store.expireActive(userId);
        return persisted.stream().map(value -> generate(userId, value, now)).toList();
    }

    private List<GeneratedRecommendation> generate(
            UUID userId, List<HoldingAnalysisApplicationService.AnalyzedHolding> analyzed) {
        var now = clock.instant();
        store.expireActive(userId);
        return analyzed.stream().map(value -> generate(userId, value, now)).toList();
    }

    private GeneratedRecommendation generate(
            UUID userId, HoldingAnalysisApplicationService.AnalyzedHolding value, java.time.Instant now) {
        var recommendationId =
                store.appendRecommendation(userId, value.snapshotId(), value.analysis(), value.resolution(), now);
        narration.generate(recommendationId, value);
        return new GeneratedRecommendation(recommendationId, value);
    }

    private GeneratedRecommendation generate(
            UUID userId, HoldingAnalysisStore.PersistedHolding value, java.time.Instant now) {
        var recommendationId =
                store.appendRecommendation(userId, value.snapshotId(), value.analysis(), value.resolution(), now);
        narration.generate(recommendationId, value.narrativeInput());
        return new GeneratedRecommendation(recommendationId, null);
    }

    public record GeneratedRecommendation(
            UUID recommendationId, HoldingAnalysisApplicationService.AnalyzedHolding analyzedHolding) {}
}
