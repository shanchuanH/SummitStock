package com.example.portfolio.backtest;

import java.time.Instant;
import java.util.Objects;

/** Enforces the first instant at which point-in-time evidence may enter a decision. */
public final class LookaheadGuard {
    public void requireAvailable(Evidence evidence, Instant decisionAt) {
        Objects.requireNonNull(evidence);
        Objects.requireNonNull(decisionAt);
        if (evidence.availableAt().isAfter(decisionAt)) {
            throw new IllegalArgumentException("BACKTEST_LOOKAHEAD_" + evidence.kind().name());
        }
    }

    public record Evidence(Kind kind, Instant availableAt) {
        public Evidence {
            Objects.requireNonNull(kind);
            Objects.requireNonNull(availableAt);
        }
    }

    public enum Kind {
        FILING,
        ESTIMATE,
        EARNINGS_RESULT
    }
}
