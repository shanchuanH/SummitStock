package com.example.portfolio.estimates;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FakeEstimateDataProviderTest {
    @Test
    void fixtureIsDeterministicAndComplete() {
        var clock = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);
        var provider = new FakeEstimateDataProvider(clock);
        var first = provider.fetchEstimates("GOOGL");
        var second = provider.fetchEstimates("GOOGL");
        assertThat(first).isEqualTo(second);
        assertThat(first.estimates()).hasSize(2);
        assertThat(first.quality().name()).isEqualTo("HEALTHY");
    }
}
