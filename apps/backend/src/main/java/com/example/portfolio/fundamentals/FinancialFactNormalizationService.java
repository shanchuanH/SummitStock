package com.example.portfolio.fundamentals;

import org.springframework.stereotype.Service;

@Service
public class FinancialFactNormalizationService {
    private final FinancialEvidenceStore store;
    private final FinancialConceptMapping mapping;
    private final FinancialPeriodResolver resolver = new FinancialPeriodResolver();
    private final FinancialMetricEngine metrics = new FinancialMetricEngine();

    public FinancialFactNormalizationService(FinancialEvidenceStore store, FinancialConceptMapping mapping) {
        this.store = store;
        this.mapping = mapping;
    }

    public int normalizeAll() {
        int affected = 0;
        for (var instrument : store.eligibleInstruments()) {
            var periods = resolver.resolve(store.facts(instrument.id()), mapping);
            for (var period : periods) {
                var periodId = store.savePeriod(instrument.id(), period);
                affected += store.saveMetrics(
                        instrument.id(), periodId, metrics.compute(period, periods), period.filedAt());
            }
        }
        return affected;
    }
}
