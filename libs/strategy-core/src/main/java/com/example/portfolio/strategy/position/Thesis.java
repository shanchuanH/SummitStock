package com.example.portfolio.strategy.position;

import java.time.Instant;
import java.util.List;

public record Thesis(
        String summary,
        List<String> confirmations,
        List<String> invalidations,
        List<String> sources,
        Status status,
        Instant expiresAt,
        boolean userConfirmed) {
    public Thesis {
        confirmations = List.copyOf(confirmations);
        invalidations = List.copyOf(invalidations);
        sources = List.copyOf(sources);
        if (summary == null || summary.isBlank() || sources.isEmpty())
            throw new IllegalArgumentException("Thesis summary and sources are required");
    }

    public boolean actionableAt(Instant now) {
        return userConfirmed && expiresAt.isAfter(now);
    }

    public enum Status {
        HEALTHY,
        WEAKENING,
        REDUCE_RECOMMENDED,
        BROKEN
    }
}
