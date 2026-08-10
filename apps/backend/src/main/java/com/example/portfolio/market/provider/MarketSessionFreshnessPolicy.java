package com.example.portfolio.market.provider;

import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public final class MarketSessionFreshnessPolicy {
    private final TradingCalendar calendar;

    public MarketSessionFreshnessPolicy(TradingCalendar calendar) {
        this.calendar = calendar;
    }

    public ProviderModels.QualityStatus decisionPriceQuality(
            LocalDate marketDate, java.math.BigDecimal price, Instant now) {
        if (marketDate == null || price == null) return ProviderModels.QualityStatus.MISSING;
        if (price.signum() <= 0) return ProviderModels.QualityStatus.SUSPECT;
        var latestCompletedSession = calendar.latestCompletedSession(now);
        if (marketDate.isAfter(latestCompletedSession)) return ProviderModels.QualityStatus.SUSPECT;
        return marketDate.equals(latestCompletedSession)
                ? ProviderModels.QualityStatus.HEALTHY
                : ProviderModels.QualityStatus.STALE;
    }
}
