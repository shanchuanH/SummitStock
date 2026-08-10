package com.example.portfolio.analysis.narrative;

import java.util.List;

public record NarrativeInput(
        String symbol,
        String classification,
        String finalAction,
        String companyHealth,
        String valuation,
        String revision,
        String priceState,
        String portfolioCapacity,
        String winningRule,
        List<String> reasons,
        List<String> risks,
        List<String> changeConditions,
        String confidence,
        boolean analystEvidenceAvailable) {
    public NarrativeInput {
        reasons = List.copyOf(reasons);
        risks = List.copyOf(risks);
        changeConditions = List.copyOf(changeConditions);
    }

    public String factText() {
        return String.join(
                        " ",
                        List.of(
                                symbol,
                                classification,
                                finalAction,
                                companyHealth,
                                valuation,
                                revision,
                                priceState,
                                portfolioCapacity,
                                winningRule,
                                confidence))
                + " " + String.join(" ", reasons)
                + " " + String.join(" ", risks)
                + " " + String.join(" ", changeConditions);
    }
}
