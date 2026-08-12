package com.example.portfolio.market.provider;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !local-fixture")
@ConditionalOnExpression("'${portfolio.providers.fundamentals.type:disabled}' != 'sec'")
final class UnavailableFundamentalsProvider implements FundamentalsProvider {
    private final Clock clock;

    UnavailableFundamentalsProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String providerId() {
        return "unavailable";
    }

    @Override
    public ProviderModels.FilingIndexResult fetchFilings(String cik) {
        return new ProviderModels.FilingIndexResult(cik, List.of(), provenance());
    }

    @Override
    public ProviderModels.CompanyFactsResult fetchCompanyFacts(String cik) {
        return new ProviderModels.CompanyFactsResult(cik, List.of(), provenance());
    }

    private ProviderModels.Provenance provenance() {
        var now = clock.instant();
        return new ProviderModels.Provenance(
                "unavailable", now, now, "unavailable", "none", ProviderModels.QualityStatus.MISSING, List.of());
    }
}
