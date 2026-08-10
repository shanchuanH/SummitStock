package com.example.portfolio.analysis.narrative;

import org.springframework.stereotype.Component;

@Component
public final class NarrativeGenerator {
    private final NarrativeModelClient model;
    private final NarrativeFactValidator validator;
    private final DeterministicNarrativeTemplate fallback;

    public NarrativeGenerator(
            NarrativeModelClient model, NarrativeFactValidator validator, DeterministicNarrativeTemplate fallback) {
        this.model = model;
        this.validator = validator;
        this.fallback = fallback;
    }

    public GeneratedNarrative generate(NarrativeInput input) {
        try {
            var generated = model.narrate(input);
            if (generated.isPresent()) {
                var candidate = generated.get();
                var validation = validator.validate(input, candidate.narrative());
                if (validation.valid()) {
                    return new GeneratedNarrative(
                            input, candidate.narrative(), "LLM", candidate.provider(), candidate.model(), "VALID");
                }
                return fallback(input, "REJECTED:" + String.join(",", validation.violations()));
            }
            return fallback(input, "MODEL_UNAVAILABLE");
        } catch (RuntimeException exception) {
            return fallback(input, "MODEL_ERROR");
        }
    }

    private GeneratedNarrative fallback(NarrativeInput input, String status) {
        return new GeneratedNarrative(
                input, fallback.render(input), "DETERMINISTIC_FALLBACK", "internal", "template-v1", status);
    }

    public record GeneratedNarrative(
            NarrativeInput input,
            DecisionNarrative narrative,
            String source,
            String provider,
            String model,
            String validationStatus) {}
}
