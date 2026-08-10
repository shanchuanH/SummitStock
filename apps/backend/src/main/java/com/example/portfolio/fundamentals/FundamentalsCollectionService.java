package com.example.portfolio.fundamentals;

import com.example.portfolio.market.provider.FundamentalsProvider;
import com.example.portfolio.market.provider.ProviderCallException;
import java.util.ArrayList;
import java.util.List;
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
        var failed = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var instrument : store.eligibleInstruments()) {
            final com.example.portfolio.market.provider.ProviderModels.FilingIndexResult result;
            try {
                result = provider.fetchFilings(instrument.cik());
            } catch (ProviderCallException exception) {
                failed.add(instrument.symbol());
                warnings.add(instrument.symbol() + ":" + exception.code());
                continue;
            }
            observations += result.filings().size();
            affected += store.saveFilingPeriods(instrument.id(), result);
        }
        return new CollectionResult(observations, affected, failed, warnings);
    }

    public CollectionResult collectFundamentals() {
        int observations = 0;
        int affected = 0;
        var failed = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var instrument : store.eligibleInstruments()) {
            final com.example.portfolio.market.provider.ProviderModels.CompanyFactsResult result;
            try {
                result = provider.fetchCompanyFacts(instrument.cik());
            } catch (ProviderCallException exception) {
                failed.add(instrument.symbol());
                warnings.add(instrument.symbol() + ":" + exception.code());
                continue;
            }
            observations += result.facts().size();
            affected += store.saveFacts(instrument.id(), result, mapping);
        }
        return new CollectionResult(observations, affected, failed, warnings);
    }

    public record CollectionResult(
            int observations, int affected, List<String> failedInstruments, List<String> warnings) {
        public CollectionResult {
            failedInstruments = List.copyOf(failedInstruments);
            warnings = List.copyOf(warnings);
        }

        public CollectionResult(int observations, int affected) {
            this(observations, affected, List.of(), List.of());
        }
    }
}
