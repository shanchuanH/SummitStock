package com.example.portfolio.analysis.replay;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;

public record DecisionAsOfContext(LocalDate marketDate, Instant dataCutoff, String strategyVersion) {
    public DecisionAsOfContext {
        Objects.requireNonNull(marketDate, "marketDate");
        Objects.requireNonNull(dataCutoff, "dataCutoff");
        if (strategyVersion == null || strategyVersion.isBlank()) {
            throw new IllegalArgumentException("strategyVersion is required");
        }
    }

    public static DecisionAsOfContext marketClose(LocalDate marketDate, String strategyVersion) {
        return new DecisionAsOfContext(
                marketDate,
                marketDate.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).minusNanos(1),
                strategyVersion);
    }
}
