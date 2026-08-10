package com.example.portfolio.market.provider;

import java.time.Duration;
import java.time.Instant;

public final class DataQualityPolicy {
    public ProviderModels.QualityStatus assess(Evidence evidence, Instant now) {
        if (evidence.availableFields() == 0) return ProviderModels.QualityStatus.MISSING;
        if (evidence.validationFailure() || evidence.unitConflict()) return ProviderModels.QualityStatus.SUSPECT;
        if (evidence.sourceTimestamp().plus(evidence.maximumAge()).isBefore(now)) {
            return ProviderModels.QualityStatus.STALE;
        }
        if (evidence.availableFields() < evidence.expectedFields()) return ProviderModels.QualityStatus.PARTIAL;
        return ProviderModels.QualityStatus.HEALTHY;
    }

    public record Evidence(
            int expectedFields,
            int availableFields,
            boolean validationFailure,
            boolean unitConflict,
            Instant sourceTimestamp,
            Duration maximumAge) {
        public Evidence {
            if (expectedFields < 1) throw new IllegalArgumentException("expectedFields must be positive");
            if (availableFields < 0) throw new IllegalArgumentException("availableFields cannot be negative");
        }
    }
}
