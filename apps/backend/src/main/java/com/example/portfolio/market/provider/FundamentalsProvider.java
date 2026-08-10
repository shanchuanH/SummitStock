package com.example.portfolio.market.provider;

import java.util.Set;

public interface FundamentalsProvider {
    default String providerId() {
        return "fundamentals";
    }

    default Set<ProviderCapability> capabilities() {
        return Set.of(ProviderCapability.FUNDAMENTALS);
    }

    ProviderModels.FilingIndexResult fetchFilings(String cik);

    ProviderModels.CompanyFactsResult fetchCompanyFacts(String cik);
}
