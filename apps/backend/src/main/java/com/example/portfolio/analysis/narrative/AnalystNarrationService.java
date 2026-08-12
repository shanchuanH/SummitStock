package com.example.portfolio.analysis.narrative;

import com.example.portfolio.analysis.application.HoldingAnalysisApplicationService;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class AnalystNarrationService {
    private final NarrativeGenerator generator;
    private final DecisionNarrativeStore store;
    private final Clock clock;

    public AnalystNarrationService(NarrativeGenerator generator, DecisionNarrativeStore store, Clock clock) {
        this.generator = generator;
        this.store = store;
        this.clock = clock;
    }

    public NarrativeGenerator.GeneratedNarrative generate(
            UUID recommendationId, HoldingAnalysisApplicationService.AnalyzedHolding analyzed) {
        return generate(recommendationId, analyzed.narrativeInput());
    }

    public NarrativeGenerator.GeneratedNarrative generate(UUID recommendationId, NarrativeInput input) {
        var generated = generator.generate(input);
        store.append(recommendationId, generated, clock.instant());
        return generated;
    }
}
