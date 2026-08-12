package com.example.portfolio.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class BacktestBiasProofEvaluator {
    Result evaluate(Proof proof) {
        var failures = new ArrayList<String>();
        if (proof.trainingEnd() == null
                || proof.outOfSampleStart() == null
                || !proof.trainingEnd().isBefore(proof.outOfSampleStart())) {
            failures.add("TRAINING_OVERLAPS_OUT_OF_SAMPLE");
        }
        if (proof.decisionAt() == null
                || proof.featureTimestamps().stream().anyMatch(value -> value.isAfter(proof.decisionAt()))) {
            failures.add("FEATURE_AFTER_DECISION");
        }
        if (!proof.allBarsCompletedAsOfDecision()) failures.add("INCOMPLETE_BAR_USED");
        requireVersion(proof.universeVersion(), "UNIVERSE_VERSION_MISSING", failures);
        requireVersion(proof.priceAdjustmentVersion(), "PRICE_ADJUSTMENT_VERSION_MISSING", failures);
        requireVersion(proof.calendarVersion(), "CALENDAR_VERSION_MISSING", failures);
        requireVersion(proof.costModelVersion(), "COST_MODEL_VERSION_MISSING", failures);
        requireVersion(proof.featureCutoffPolicy(), "FEATURE_CUTOFF_POLICY_MISSING", failures);
        return new Result(failures.isEmpty() ? "CLEAR" : "BLOCKED", failures);
    }

    private static void requireVersion(String value, String failure, List<String> failures) {
        if (value == null || value.isBlank()) failures.add(failure);
    }

    record Proof(
            LocalDate trainingEnd,
            LocalDate outOfSampleStart,
            Instant decisionAt,
            List<Instant> featureTimestamps,
            boolean allBarsCompletedAsOfDecision,
            String universeVersion,
            String priceAdjustmentVersion,
            String calendarVersion,
            String costModelVersion,
            String featureCutoffPolicy) {
        Proof {
            featureTimestamps = featureTimestamps == null ? List.of() : List.copyOf(featureTimestamps);
        }
    }

    record Result(String status, List<String> failures) {
        Result {
            failures = List.copyOf(failures);
        }
    }
}
