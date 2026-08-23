package com.example.portfolio.analysis.replay;

import com.example.portfolio.market.provider.TradingCalendar;
import com.example.portfolio.market.provider.UsEquityTradingCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

public record DecisionAsOfContext(LocalDate marketDate, Instant dataCutoff, String strategyVersion) {
    public DecisionAsOfContext {
        Objects.requireNonNull(marketDate, "marketDate");
        Objects.requireNonNull(dataCutoff, "dataCutoff");
        if (strategyVersion == null || strategyVersion.isBlank()) {
            throw new IllegalArgumentException("strategyVersion is required");
        }
    }

    public static DecisionAsOfContext marketClose(LocalDate marketDate, String strategyVersion) {
        return marketClose(marketDate, strategyVersion, new UsEquityTradingCalendar());
    }

    public static DecisionAsOfContext marketClose(
            LocalDate marketDate, String strategyVersion, TradingCalendar calendar) {
        return new DecisionAsOfContext(marketDate, calendar.sessionClose(marketDate), strategyVersion);
    }

    public boolean includes(LocalDate evidenceMarketDate, Instant evidenceDataAsOf) {
        return evidenceMarketDate != null
                && evidenceDataAsOf != null
                && !evidenceMarketDate.isAfter(marketDate)
                && !evidenceDataAsOf.isAfter(dataCutoff);
    }
}
