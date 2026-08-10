package com.example.portfolio.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestBiasProofEvaluatorTest {
    private final BacktestBiasProofEvaluator evaluator = new BacktestBiasProofEvaluator();

    @Test
    void clearsOnlyCompletePointInTimeProof() {
        var result = evaluator.evaluate(proof(
                LocalDate.parse("2023-12-29"),
                LocalDate.parse("2024-01-02"),
                List.of(Instant.parse("2026-08-05T11:59:59Z")),
                true,
                "sp500-2026-08"));
        assertThat(result.status()).isEqualTo("CLEAR");
        assertThat(result.failures()).isEmpty();
    }

    @Test
    void blocksOverlappingFoldsFutureFeaturesIncompleteBarsAndMissingVersions() {
        var result = evaluator.evaluate(proof(
                LocalDate.parse("2024-01-02"),
                LocalDate.parse("2024-01-02"),
                List.of(Instant.parse("2026-08-05T12:00:01Z")),
                false,
                " "));
        assertThat(result.status()).isEqualTo("BLOCKED");
        assertThat(result.failures())
                .containsExactly(
                        "TRAINING_OVERLAPS_OUT_OF_SAMPLE",
                        "FEATURE_AFTER_DECISION",
                        "INCOMPLETE_BAR_USED",
                        "UNIVERSE_VERSION_MISSING");
    }

    private static BacktestBiasProofEvaluator.Proof proof(
            LocalDate trainingEnd,
            LocalDate outOfSampleStart,
            List<Instant> featureTimestamps,
            boolean allBarsCompleted,
            String universeVersion) {
        return new BacktestBiasProofEvaluator.Proof(
                trainingEnd,
                outOfSampleStart,
                Instant.parse("2026-08-05T12:00:00Z"),
                featureTimestamps,
                allBarsCompleted,
                universeVersion,
                "split-dividend-v2",
                "xnys-2026a",
                "close-slippage-10bps-v1",
                "completed-bars-only-v1");
    }
}
