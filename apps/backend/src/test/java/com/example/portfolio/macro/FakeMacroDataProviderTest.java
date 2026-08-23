package com.example.portfolio.macro;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class FakeMacroDataProviderTest {
    @Test
    void fixtureProvidesDeterministicHistoryRatherThanMissingData() {
        var clock = Clock.fixed(Instant.parse("2026-08-20T12:00:00Z"), ZoneOffset.UTC);
        var provider = new FakeMacroDataProvider(clock);
        var result = provider.fetch("VIXCLS", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1));
        assertThat(result.observations()).hasSizeGreaterThan(5);
        assertThat(result.provider()).isEqualTo("fixture-macro");
        assertThat(result.quality().name()).isEqualTo("HEALTHY");
    }
}
