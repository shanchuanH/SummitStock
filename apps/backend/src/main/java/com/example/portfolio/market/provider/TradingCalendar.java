package com.example.portfolio.market.provider;

import java.time.Instant;
import java.time.LocalDate;

public interface TradingCalendar {
    LocalDate latestCompletedSession(Instant now);

    boolean isSession(LocalDate date);
}
