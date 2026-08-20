package com.example.portfolio.estimates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EstimateConsensusMathTest {
    @Test
    void reconstructsPriorConsensusFromPersistedFractionalRevision() {
        assertThat(EstimateConsensusMath.priorConsensus(new BigDecimal("8.42"), new BigDecimal("0.020606")))
                .isCloseTo(new BigDecimal("8.25"), within(new BigDecimal("0.000001")));
    }

    @Test
    void missingOrInvalidChangeDoesNotInventPriorConsensus() {
        assertThat(EstimateConsensusMath.priorConsensus(new BigDecimal("8.42"), null))
                .isNull();
        assertThat(EstimateConsensusMath.priorConsensus(new BigDecimal("8.42"), new BigDecimal("-1")))
                .isNull();
    }

    @Test
    void highDispersionUsesTheCanonicalEstimateQualityBoundary() {
        assertThat(EstimateRevisionEngine.isHighDispersion(new BigDecimal("0.50")))
                .isFalse();
        assertThat(EstimateRevisionEngine.isHighDispersion(new BigDecimal("0.5001")))
                .isTrue();
    }
}
