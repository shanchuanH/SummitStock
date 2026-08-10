package com.example.portfolio.earnings;

import com.example.portfolio.market.provider.ProviderModels;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${portfolio.providers.earnings-calendar.type:unavailable}' != 'alpha-vantage'")
public final class UnavailableEarningsCalendarProvider implements EarningsCalendarProvider {
    private final Clock clock;

    public UnavailableEarningsCalendarProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public CalendarResult fetch(String symbol, LocalDate from, LocalDate to) {
        return new CalendarResult(
                symbol, List.of(), "unavailable", clock.instant(), ProviderModels.QualityStatus.MISSING);
    }
}
