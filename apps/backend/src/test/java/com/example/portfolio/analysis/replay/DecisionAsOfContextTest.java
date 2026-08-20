package com.example.portfolio.analysis.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DecisionAsOfContextTest {
    @Test
    void marketCloseIncludesOnlyEvidenceKnownByTheEndOfThatUtcDate() {
        var context = DecisionAsOfContext.marketClose(LocalDate.parse("2026-01-10"), "3.0.0-draft");
        assertThat(context.marketDate()).isEqualTo("2026-01-10");
        assertThat(context.dataCutoff()).isEqualTo(Instant.parse("2026-01-10T23:59:59.999999999Z"));
        assertThat(context.strategyVersion()).isEqualTo("3.0.0-draft");
    }

    @Test
    void rejectsAnUnversionedReplay() {
        assertThatThrownBy(() -> new DecisionAsOfContext(LocalDate.now(), Instant.now(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
