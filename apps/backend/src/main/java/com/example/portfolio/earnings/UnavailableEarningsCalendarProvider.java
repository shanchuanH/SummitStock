package com.example.portfolio.earnings;

import com.example.portfolio.market.provider.ProviderModels;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class UnavailableEarningsCalendarProvider implements EarningsCalendarProvider {
    private final Clock clock;

    UnavailableEarningsCalendarProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public CalendarResult fetch(String symbol, LocalDate from, LocalDate to) {
        return new CalendarResult(
                symbol, List.of(), "unavailable", clock.instant(), ProviderModels.QualityStatus.MISSING);
    }
}
