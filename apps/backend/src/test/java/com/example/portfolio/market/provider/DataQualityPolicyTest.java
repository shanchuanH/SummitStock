package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DataQualityPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-05T16:00:00Z");
    private final DataQualityPolicy policy = new DataQualityPolicy();

    @Test
    void classifiesEveryTruthfulQualityState() {
        assertThat(assess(3, 3, false, false, NOW)).isEqualTo(ProviderModels.QualityStatus.HEALTHY);
        assertThat(assess(3, 2, false, false, NOW)).isEqualTo(ProviderModels.QualityStatus.PARTIAL);
        assertThat(assess(3, 3, false, false, NOW.minus(Duration.ofDays(3))))
                .isEqualTo(ProviderModels.QualityStatus.STALE);
        assertThat(assess(3, 3, true, false, NOW)).isEqualTo(ProviderModels.QualityStatus.SUSPECT);
        assertThat(assess(3, 3, false, true, NOW)).isEqualTo(ProviderModels.QualityStatus.SUSPECT);
        assertThat(assess(3, 0, false, false, NOW)).isEqualTo(ProviderModels.QualityStatus.MISSING);
    }

    @Test
    void emptyEvidenceCanNeverBeHealthy() {
        assertThat(assess(1, 0, false, false, NOW)).isNotEqualTo(ProviderModels.QualityStatus.HEALTHY);
    }

    private ProviderModels.QualityStatus assess(
            int expected, int available, boolean invalid, boolean unitConflict, Instant sourceTimestamp) {
        return policy.assess(
                new DataQualityPolicy.Evidence(
                        expected, available, invalid, unitConflict, sourceTimestamp, Duration.ofDays(1)),
                NOW);
    }
}
