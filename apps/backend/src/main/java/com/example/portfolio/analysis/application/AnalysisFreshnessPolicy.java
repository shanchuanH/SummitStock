package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.StrategyDefinition;
import com.example.portfolio.market.provider.TradingCalendar;
import com.example.portfolio.market.provider.UsEquityTradingCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class AnalysisFreshnessPolicy {
    private final TradingCalendar calendar;

    public AnalysisFreshnessPolicy() {
        this(new UsEquityTradingCalendar());
    }

    @Autowired
    public AnalysisFreshnessPolicy(TradingCalendar calendar) {
        this.calendar = calendar;
    }

    public boolean stalePrice(LocalDate marketDate, Instant now, StrategyDefinition.FreshnessPolicy policy) {
        if (marketDate == null) return true;
        LocalDate latest = calendar.latestCompletedSession(now);
        if (marketDate.isAfter(latest)) return false;
        int elapsedSessions = 0;
        for (LocalDate date = marketDate.plusDays(1); !date.isAfter(latest); date = date.plusDays(1)) {
            if (calendar.isSession(date)) elapsedSessions++;
        }
        return elapsedSessions > policy.eodPriceTradingSessions();
    }

    public boolean staleDays(Instant dataAsOf, Instant now, int maximumAgeDays) {
        return dataAsOf == null || dataAsOf.isBefore(now.minus(maximumAgeDays, ChronoUnit.DAYS));
    }
}
