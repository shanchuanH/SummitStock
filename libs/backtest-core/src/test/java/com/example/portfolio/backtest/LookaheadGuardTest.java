package com.example.portfolio.backtest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LookaheadGuardTest {
    @ParameterizedTest
    @EnumSource(LookaheadGuard.Kind.class)
    void evidenceIsUsableOnlyAfterItsPointInTimeAvailability(LookaheadGuard.Kind kind) {
        var guard = new LookaheadGuard();
        var available = Instant.parse("2026-02-03T21:00:00Z");
        var evidence = new LookaheadGuard.Evidence(kind, available);
        assertThatThrownBy(() -> guard.requireAvailable(evidence, available.minusNanos(1)))
                .hasMessage("BACKTEST_LOOKAHEAD_" + kind.name());
        assertThatCode(() -> guard.requireAvailable(evidence, available)).doesNotThrowAnyException();
    }
}
