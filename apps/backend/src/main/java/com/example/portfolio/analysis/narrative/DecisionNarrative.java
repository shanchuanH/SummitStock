package com.example.portfolio.analysis.narrative;

import java.util.List;

public record DecisionNarrative(
        String headline,
        String oneSentence,
        List<String> why,
        List<String> risks,
        List<String> watchNext,
        String confidenceExplanation) {
    public DecisionNarrative {
        why = List.copyOf(why);
        risks = List.copyOf(risks);
        watchNext = List.copyOf(watchNext);
    }

    public String text() {
        return String.join(" ", List.of(headline, oneSentence, confidenceExplanation))
                + " " + String.join(" ", why)
                + " " + String.join(" ", risks)
                + " " + String.join(" ", watchNext);
    }
}
