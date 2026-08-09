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
        var evidence = analyzed.evidence();
        var analysis = analyzed.analysis();
        var resolution = analyzed.resolution();
        var input = new NarrativeInput(
                evidence.instrument().symbol(),
                evidence.position().classification().name(),
                resolution.winner().action().name(),
                evidence.fundamentals().financialHealth(),
                evidence.valuation().state(),
                evidence.fundamentals().estimateRevision(),
                evidence.indicators().priceState(),
                resolution.winner().riskRank() <= 7 ? "BLOCKED" : "AVAILABLE",
                resolution.winner().ruleId(),
                analysis.reasons(),
                analysis.risks(),
                analysis.changeConditions(),
                analysis.confidence(),
                false);
        var generated = generator.generate(input);
        store.append(recommendationId, generated, clock.instant());
        return generated;
    }
}
