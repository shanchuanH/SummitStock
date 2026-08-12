package com.example.portfolio.market.provider;

import java.time.Instant;
import java.time.LocalDate;

public interface TradingCalendar {
    LocalDate latestCompletedSession(Instant now);

    boolean isSession(LocalDate date);

    Instant sessionOpen(LocalDate date);

    Instant sessionClose(LocalDate date);

    boolean isEarlyClose(LocalDate date);

    default int sessionsBetween(LocalDate fromExclusive, LocalDate toInclusive) {
        if (fromExclusive == null || toInclusive == null || !toInclusive.isAfter(fromExclusive)) return 0;
        int sessions = 0;
        for (var date = fromExclusive.plusDays(1); !date.isAfter(toInclusive); date = date.plusDays(1)) {
            if (isSession(date)) sessions++;
        }
        return sessions;
    }

    default LocalDate plusSessions(LocalDate from, int sessions) {
        if (from == null || sessions < 0)
            throw new IllegalArgumentException("A date and non-negative sessions are required");
        var date = from;
        for (int added = 0; added < sessions; ) {
            date = date.plusDays(1);
            if (isSession(date)) added++;
        }
        return date;
    }

    default LocalDate previousSession(LocalDate from) {
        var date = from.minusDays(1);
        while (!isSession(date)) date = date.minusDays(1);
        return date;
    }

    default LocalDate nextSession(LocalDate from) {
        return plusSessions(from, 1);
    }

    default String calendarVersion() {
        return "XNYS-XNAS-rules-2026.1";
    }
}
