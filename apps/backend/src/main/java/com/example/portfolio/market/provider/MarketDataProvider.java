package com.example.portfolio.market.provider;

import java.time.LocalDate;

public interface MarketDataProvider {
    ProviderModels.DailyBarsResult fetchDailyBars(String symbol, LocalDate from, LocalDate to);

    ProviderModels.QuoteResult fetchQuote(String symbol);

    ProviderModels.CorporateActionsResult fetchCorporateActions(String symbol, LocalDate from, LocalDate to);
}
