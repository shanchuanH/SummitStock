package com.example.portfolio.fundamentals;

import com.example.portfolio.market.provider.FundamentalsProvider;
import org.springframework.stereotype.Service;

@Service
public class FundamentalsCollectionService {
    private final FundamentalsProvider provider;
    private final FinancialEvidenceStore store;
    private final FinancialConceptMapping mapping;

    public FundamentalsCollectionService(
            FundamentalsProvider provider, FinancialEvidenceStore store, FinancialConceptMapping mapping) {
        this.provider = provider;
        this.store = store;
        this.mapping = mapping;
    }

    public CollectionResult checkFilings() {
        int observations = 0;
        int affected = 0;
        for (var instrument : store.eligibleInstruments()) {
            var result = provider.fetchFilings(instrument.cik());
            observations += result.filings().size();
            affected += store.saveFilingPeriods(instrument.id(), result);
        }
        return new CollectionResult(observations, affected);
    }

    public CollectionResult collectFundamentals() {
        int observations = 0;
        int affected = 0;
        for (var instrument : store.eligibleInstruments()) {
            var result = provider.fetchCompanyFacts(instrument.cik());
            observations += result.facts().size();
            affected += store.saveFacts(instrument.id(), result, mapping);
        }
        return new CollectionResult(observations, affected);
    }

    public record CollectionResult(int observations, int affected) {}
}
