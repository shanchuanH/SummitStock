package com.example.portfolio.earnings;

import com.example.portfolio.market.provider.ProviderModels;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Deterministic raw calendar evidence for full pipeline integration tests. */
@Component
@Primary
@Profile("test")
@ConditionalOnProperty(name = "portfolio.test.complete-provider-fixtures", havingValue = "true")
final class FakeEarningsCalendarProvider implements EarningsCalendarProvider {
    private final Clock clock;

    FakeEarningsCalendarProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public CalendarResult fetch(String symbol, LocalDate from, LocalDate to) {
        var marketDate = LocalDate.now(clock).plusDays(20);
        var event = new CalendarEvent(
                marketDate.atTime(20, 0).toInstant(ZoneOffset.UTC),
                marketDate,
                Timing.AFTER_CLOSE,
                "FY" + marketDate.getYear() + "Q3",
                "DXYZ".equals(symbol),
                "https://fixture.events/" + symbol);
        return new CalendarResult(
                symbol, List.of(event), "fixture-calendar", clock.instant(), ProviderModels.QualityStatus.HEALTHY);
    }
}
