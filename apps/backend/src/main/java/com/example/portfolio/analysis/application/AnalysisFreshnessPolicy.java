package com.example.portfolio.analysis.application;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public final class AnalysisFreshnessPolicy {
    private static final Duration MAX_MARKET_AGE = Duration.ofHours(36);

    public boolean stale(Instant dataAsOf, Instant now) {
        return dataAsOf == null || dataAsOf.isBefore(now.minus(MAX_MARKET_AGE));
    }
}
