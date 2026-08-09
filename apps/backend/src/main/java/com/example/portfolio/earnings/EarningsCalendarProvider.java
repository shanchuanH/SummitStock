package com.example.portfolio.earnings;

import com.example.portfolio.market.provider.ProviderModels;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface EarningsCalendarProvider {
    CalendarResult fetch(String symbol, LocalDate from, LocalDate to);

    record CalendarEvent(
            Instant eventAt,
            LocalDate marketDate,
            Timing timing,
            String fiscalPeriod,
            boolean binaryEvent,
            String sourceUrl) {}

    record CalendarResult(
            String symbol,
            List<CalendarEvent> events,
            String provider,
            Instant dataAsOf,
            ProviderModels.QualityStatus quality) {
        public CalendarResult {
            events = List.copyOf(events);
        }
    }

    enum Timing {
        BEFORE_OPEN,
        AFTER_CLOSE,
        DURING_MARKET,
        UNKNOWN
    }
}
