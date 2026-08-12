package com.example.portfolio.analysis.domain;

public enum DecisionChannel {
    RISK_REDUCTION,
    NEW_RISK,
    MAINTENANCE;

    public static DecisionChannel forAction(RecommendationAction action) {
        return switch (action) {
            case EXIT, REDUCE_HALF, TRIM -> RISK_REDUCTION;
            case BUY, STARTER_BUY, ADD, DEPLOY_DIP_TRANCHE, PAUSE_NEW_RISK, DO_NOT_ADD, WAIT_FOR_DATA -> NEW_RISK;
            default -> MAINTENANCE;
        };
    }
}
