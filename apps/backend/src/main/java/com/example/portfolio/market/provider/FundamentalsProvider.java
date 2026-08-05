package com.example.portfolio.market.provider;

public interface FundamentalsProvider {
    ProviderModels.FilingIndexResult fetchFilings(String cik);

    ProviderModels.CompanyFactsResult fetchCompanyFacts(String cik);
}
