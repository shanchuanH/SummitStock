package com.example.portfolio.analysis.decision;

import com.example.portfolio.analysis.domain.RecommendationAction;

public final class RecommendationSizingService {
    private RecommendationSizingService() {}

    public static boolean requiresBuySizing(RecommendationAction action) {
        return action == RecommendationAction.BUY
                || action == RecommendationAction.STARTER_BUY
                || action == RecommendationAction.ADD
                || action == RecommendationAction.DEPLOY_DIP_TRANCHE;
    }

    public static boolean requiresSellSizing(RecommendationAction action) {
        return action == RecommendationAction.TRIM
                || action == RecommendationAction.REDUCE_HALF
                || action == RecommendationAction.EXIT;
    }
}
