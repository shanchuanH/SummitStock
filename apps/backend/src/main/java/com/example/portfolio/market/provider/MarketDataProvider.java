package com.example.portfolio.market.provider;

import java.time.LocalDate;
import java.util.Set;

public interface MarketDataProvider {
    String providerId();

    default Set<ProviderCapability> capabilities() {
        return Set.of(
                ProviderCapability.EOD_BARS,
                ProviderCapability.ADJUSTED_BARS,
                ProviderCapability.QUOTE_LAST,
                ProviderCapability.CORPORATE_ACTIONS);
    }

    ProviderModels.DailyBarsResult fetchDailyBars(String symbol, LocalDate from, LocalDate to);

    ProviderModels.QuoteResult fetchQuote(String symbol);

    ProviderModels.CorporateActionsResult fetchCorporateActions(String symbol, LocalDate from, LocalDate to);
}
