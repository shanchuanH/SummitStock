package com.example.portfolio.estimates;

import org.springframework.stereotype.Service;

@Service
public class EstimateCollectionService {
    private final EstimateDataProvider provider;
    private final EstimateEvidenceStore store;

    public EstimateCollectionService(EstimateDataProvider provider, EstimateEvidenceStore store) {
        this.provider = provider;
        this.store = store;
    }

    public CollectionResult collectAll() {
        int observations = 0;
        int affected = 0;
        for (var instrument : store.eligibleInstruments()) {
            var result = provider.fetchEstimates(instrument.symbol());
            observations += result.estimates().size();
            affected += store.save(instrument.id(), result);
        }
        return new CollectionResult(observations, affected);
    }

    public record CollectionResult(int observations, int affected) {}
}
