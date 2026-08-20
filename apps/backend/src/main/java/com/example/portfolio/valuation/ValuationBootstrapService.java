package com.example.portfolio.valuation;

import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class ValuationBootstrapService {
    private final ValuationEvidenceStore store;
    private final PointInTimeValuationAssembler assembler = new PointInTimeValuationAssembler();

    public ValuationBootstrapService(ValuationEvidenceStore store) {
        this.store = store;
    }

    public int bootstrap(LocalDate marketDate) {
        int affected = 0;
        for (var instrument : store.valuationInstruments()) {
            var metrics = store.pointInTimeMetrics(instrument.id());
            var estimates = store.pointInTimeEstimates(instrument.id());
            for (var price : store.weeklyPrices(instrument.id(), marketDate.minusYears(5), marketDate)) {
                var inputs = assembler.assemble(price.marketDate(), metrics, estimates);
                if (inputs.commonShares() == null) continue;
                var row = new ValuationEvidenceStore.InputRow(
                        instrument.id(),
                        instrument.symbol(),
                        price.marketDate(),
                        price.price(),
                        inputs.trailingEps(),
                        inputs.forwardEps(),
                        inputs.revenue(),
                        inputs.freeCashFlow(),
                        inputs.cash(),
                        inputs.totalDebt(),
                        inputs.commonShares(),
                        null,
                        null,
                        null);
                affected += store.saveBootstrapMetrics(
                        instrument.id(), price.marketDate(), ValuationApplicationService.metrics(row));
            }
        }
        return affected;
    }
}
