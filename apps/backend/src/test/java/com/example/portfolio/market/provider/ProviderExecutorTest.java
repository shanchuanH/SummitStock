package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProviderExecutionPolicyTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-10T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void retriesTransientFailuresWithBackoff() {
        var attempts = new AtomicInteger();
        var sleeps = new ArrayList<Duration>();
        var executor = new ProviderExecutionPolicy(CLOCK, sleeps::add, 3, Duration.ofSeconds(2), Duration.ZERO);

        var value = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new ProviderCallException("PROVIDER_TIMEOUT", "timeout", null, true);
            }
            return "ok";
        });

        assertThat(value).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    void doesNotRetryMalformedPayloads() {
        var attempts = new AtomicInteger();
        var executor = new ProviderExecutionPolicy(CLOCK, ignored -> {}, 3, Duration.ofSeconds(1), Duration.ZERO);

        assertThatThrownBy(() -> executor.execute(() -> {
                    attempts.incrementAndGet();
                    throw new ProviderCallException("PROVIDER_MALFORMED", "bad payload", 200, false);
                }))
                .isInstanceOf(ProviderCallException.class)
                .hasMessage("bad payload");
        assertThat(attempts).hasValue(1);
    }

    @Test
    void rateLimitsCallsAndProvenanceExposesFreshness() {
        var sleeps = new ArrayList<Duration>();
        var executor = new ProviderExecutionPolicy(CLOCK, sleeps::add, 1, Duration.ZERO, Duration.ofSeconds(5));

        executor.execute(() -> "first");
        executor.execute(() -> "second");

        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
        var stale = new ProviderModels.Provenance(
                "fake",
                Instant.parse("2026-01-01T00:00:00Z"),
                CLOCK.instant(),
                "checksum",
                "v1",
                ProviderModels.QualityStatus.STALE,
                java.util.List.of("stale"));
        assertThat(stale.freshAt(Instant.parse("2025-12-31T00:00:00Z"))).isFalse();
    }
}
