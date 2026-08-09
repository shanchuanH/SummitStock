package com.example.portfolio.estimates;

public final class EstimateRevisionPolicy {
    private EstimateRevisionPolicy() {}

    public static boolean normalAddAllowed(EstimateRevisionEngine.RevisionState state) {
        return state == EstimateRevisionEngine.RevisionState.STRONGLY_POSITIVE
                || state == EstimateRevisionEngine.RevisionState.POSITIVE
                || state == EstimateRevisionEngine.RevisionState.FLAT;
    }

    public static boolean deepDiscountStarterAllowed(EstimateRevisionEngine.RevisionState state) {
        return state != EstimateRevisionEngine.RevisionState.STRONGLY_NEGATIVE;
    }

    public static boolean blocksQualityAdd(EstimateRevisionEngine.RevisionState state) {
        return state == EstimateRevisionEngine.RevisionState.STRONGLY_NEGATIVE;
    }

    public static String confidence(EstimateRevisionEngine.RevisionResult result) {
        return switch (result.quality()) {
            case HEALTHY -> "HIGH";
            case PARTIAL -> "LOW";
            default -> "MISSING";
        };
    }
}
