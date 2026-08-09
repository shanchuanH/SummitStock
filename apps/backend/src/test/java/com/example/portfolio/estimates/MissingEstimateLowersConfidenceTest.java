package com.example.portfolio.estimates;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MissingEstimateLowersConfidenceTest {
    @Test
    void unavailableEstimateEvidenceIsExplicitAndCannotReceiveHighConfidence() {
        var result = new EstimateRevisionEngine().evaluate(List.of(), Instant.parse("2026-08-01T00:00:00Z"));

        assertThat(result.overall()).isEqualTo(EstimateRevisionEngine.RevisionState.MISSING);
        assertThat(EstimateRevisionPolicy.confidence(result)).isEqualTo("MISSING");
        assertThat(EstimateRevisionPolicy.normalAddAllowed(result.overall())).isFalse();
        assertThat(EstimateRevisionPolicy.deepDiscountStarterAllowed(result.overall()))
                .isTrue();
    }
}
