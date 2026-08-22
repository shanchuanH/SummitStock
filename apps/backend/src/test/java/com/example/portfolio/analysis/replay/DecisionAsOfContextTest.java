package com.example.portfolio.analysis.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class DecisionAsOfContextTest {
    @Test
    void regularMarketCloseUsesFourPmNewYorkAndTracksDst() {
        var winter = DecisionAsOfContext.marketClose(LocalDate.parse("2026-03-06"), "3.0.0-draft");
        var summer = DecisionAsOfContext.marketClose(LocalDate.parse("2026-03-09"), "3.0.0-draft");

        assertThat(winter.dataCutoff()).isEqualTo(Instant.parse("2026-03-06T21:00:00Z"));
        assertThat(summer.dataCutoff()).isEqualTo(Instant.parse("2026-03-09T20:00:00Z"));
        assertThat(summer.strategyVersion()).isEqualTo("3.0.0-draft");
    }

    @Test
    void earlyCloseUsesOnePmNewYorkAndLaterEvidenceWaitsForNextSession() {
        var earlyClose = DecisionAsOfContext.marketClose(LocalDate.parse("2026-11-27"), "3.0.0-draft");
        var afterClose = Instant.parse("2026-11-27T18:00:01Z");

        assertThat(earlyClose.dataCutoff()).isEqualTo(Instant.parse("2026-11-27T18:00:00Z"));
        assertThat(earlyClose.includes(LocalDate.parse("2026-11-27"), afterClose))
                .isFalse();

        var nextSession = DecisionAsOfContext.marketClose(LocalDate.parse("2026-11-30"), "3.0.0-draft");
        assertThat(nextSession.includes(LocalDate.parse("2026-11-27"), afterClose))
                .isTrue();
    }

    @Test
    void rejectsAnUnversionedReplay() {
        assertThatThrownBy(() -> new DecisionAsOfContext(LocalDate.now(), Instant.now(), " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
