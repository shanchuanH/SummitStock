package com.example.portfolio.market.provider;

import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local-fixture", "test"})
public class FakeSecFundamentalsProvider implements FundamentalsProvider {
    private final Clock clock = Clock.systemUTC();

    @Override
    public String providerId() {
        return "fake-sec-ir";
    }

    @Override
    public ProviderModels.FilingIndexResult fetchFilings(String cik) {
        return new ProviderModels.FilingIndexResult(cik, List.of(), provenance(cik));
    }

    @Override
    public ProviderModels.CompanyFactsResult fetchCompanyFacts(String cik) {
        return new ProviderModels.CompanyFactsResult(cik, List.of(), provenance(cik));
    }

    private ProviderModels.Provenance provenance(String checksum) {
        var now = clock.instant();
        return new ProviderModels.Provenance(
                "fake-sec-ir",
                now,
                now,
                String.format("%064x", checksum.hashCode()),
                "v1",
                ProviderModels.QualityStatus.MISSING,
                List.of("FIXTURE_HAS_NO_FUNDAMENTALS"));
    }
}
