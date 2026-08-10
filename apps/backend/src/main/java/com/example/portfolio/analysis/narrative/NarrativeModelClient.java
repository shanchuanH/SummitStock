package com.example.portfolio.analysis.narrative;

import java.util.Optional;

public interface NarrativeModelClient {
    Optional<ModelNarrative> narrate(NarrativeInput input);

    record ModelNarrative(DecisionNarrative narrative, String provider, String model) {}
}
