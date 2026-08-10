package com.example.portfolio.analysis.domain;

public enum EvidenceDependency {
    INDEPENDENT_RISK_REDUCTION,
    NEW_RISK_ELIGIBILITY,
    MAINTENANCE;

    public static EvidenceDependency forAction(RecommendationAction action) {
        return switch (DecisionChannel.forAction(action)) {
            case RISK_REDUCTION -> INDEPENDENT_RISK_REDUCTION;
            case NEW_RISK -> NEW_RISK_ELIGIBILITY;
            case MAINTENANCE -> MAINTENANCE;
        };
    }
}
