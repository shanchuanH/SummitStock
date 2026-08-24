package com.example.portfolio.market.provider;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !local-fixture")
@ConditionalOnExpression(
        "'${portfolio.providers.market.type:disabled}' != 'alpha-vantage' && '${portfolio.providers.market.type:disabled}' != 'yahoo'")
final class UnavailableMarketDataProvider implements MarketDataProvider {
    private final Clock clock;

    UnavailableMarketDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String providerId() {
        return "unavailable";
    }

    @Override
    public ProviderModels.DailyBarsResult fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
        return new ProviderModels.DailyBarsResult(symbol, List.of(), provenance());
    }

    @Override
    public ProviderModels.QuoteResult fetchQuote(String symbol) {
        return new ProviderModels.QuoteResult(
                symbol,
                null,
                null,
                null,
                "USD",
                new ProviderModels.DecisionPriceEvidence(null, null, ProviderModels.QualityStatus.MISSING),
                new ProviderModels.ExecutionLiquidityEvidence(null, null, null, ProviderModels.QualityStatus.MISSING),
                provenance());
    }

    @Override
    public ProviderModels.CorporateActionsResult fetchCorporateActions(String symbol, LocalDate from, LocalDate to) {
        return new ProviderModels.CorporateActionsResult(symbol, List.of(), provenance());
    }

    private ProviderModels.Provenance provenance() {
        var now = clock.instant();
        return new ProviderModels.Provenance(
                "unavailable", now, now, "unavailable", "none", ProviderModels.QualityStatus.MISSING, List.of());
    }
}
