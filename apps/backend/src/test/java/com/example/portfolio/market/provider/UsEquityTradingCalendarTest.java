package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class UsEquityTradingCalendarTest {
    private final TradingCalendar calendar = new UsEquityTradingCalendar();

    @Test
    void observesGoodFridayAndExchangeHolidays() {
        assertThat(calendar.isSession(LocalDate.parse("2026-04-03"))).isFalse();
        assertThat(calendar.isSession(LocalDate.parse("2026-07-03"))).isFalse();
        assertThat(calendar.sessionsBetween(LocalDate.parse("2026-04-02"), LocalDate.parse("2026-04-06")))
                .isEqualTo(1);
    }

    @Test
    void usesEarlyCloseForSessionCompletion() {
        var earlyClose = LocalDate.parse("2026-11-27");

        assertThat(calendar.isEarlyClose(earlyClose)).isTrue();
        assertThat(calendar.sessionClose(earlyClose)).isEqualTo(Instant.parse("2026-11-27T18:00:00Z"));
        assertThat(calendar.latestCompletedSession(Instant.parse("2026-11-27T17:59:59Z")))
                .isEqualTo(LocalDate.parse("2026-11-25"));
        assertThat(calendar.latestCompletedSession(Instant.parse("2026-11-27T18:00:00Z")))
                .isEqualTo(earlyClose);
    }

    @Test
    void sessionBoundaryTracksNewYorkDst() {
        assertThat(calendar.sessionClose(LocalDate.parse("2026-03-06")))
                .isEqualTo(Instant.parse("2026-03-06T21:00:00Z"));
        assertThat(calendar.sessionClose(LocalDate.parse("2026-03-09")))
                .isEqualTo(Instant.parse("2026-03-09T20:00:00Z"));
        assertThat(calendar.sessionOpen(LocalDate.parse("2026-03-09")))
                .isEqualTo(Instant.parse("2026-03-09T13:30:00Z"));
    }

    @Test
    void sharedSessionArithmeticSkipsClosures() {
        assertThat(calendar.plusSessions(LocalDate.parse("2026-04-02"), 1)).isEqualTo(LocalDate.parse("2026-04-06"));
        assertThat(calendar.previousSession(LocalDate.parse("2026-04-06"))).isEqualTo(LocalDate.parse("2026-04-02"));
        assertThat(calendar.calendarVersion()).isEqualTo("XNYS-XNAS-rules-2026.1");
    }
}
