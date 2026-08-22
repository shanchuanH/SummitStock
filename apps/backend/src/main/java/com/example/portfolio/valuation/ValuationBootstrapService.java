package com.example.portfolio.valuation;

import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.market.provider.TradingCalendar;
import org.springframework.stereotype.Service;

@Service
public class ValuationBootstrapService {
    private final ValuationEvidenceStore store;
    private final TradingCalendar tradingCalendar;
    private final PointInTimeValuationAssembler assembler = new PointInTimeValuationAssembler();

    public ValuationBootstrapService(ValuationEvidenceStore store, TradingCalendar tradingCalendar) {
        this.store = store;
        this.tradingCalendar = tradingCalendar;
    }

    public int bootstrap(DecisionAsOfContext context) {
        int affected = 0;
        for (var instrument : store.valuationInstruments()) {
            var metrics = store.pointInTimeMetrics(instrument.id());
            var estimates = store.pointInTimeEstimates(instrument.id());
            for (var price : store.weeklyPrices(
                    instrument.id(), context.marketDate().minusYears(5), context.marketDate(), context.dataCutoff())) {
                if (!tradingCalendar.isSession(price.marketDate())) continue;
                var completedClose = tradingCalendar.sessionClose(price.marketDate());
                var priceAvailability = price.dataAsOf().isAfter(completedClose) ? price.dataAsOf() : completedClose;
                var cutoff = price.marketDate().equals(context.marketDate())
                        ? context.dataCutoff()
                        : tradingCalendar.sessionClose(price.marketDate());
                var pointInTime = assembler.assemble(price.marketDate(), cutoff, priceAvailability, metrics, estimates);
                var inputs = pointInTime.inputs();
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
                        instrument.id(),
                        price.marketDate(),
                        ValuationApplicationService.metrics(row),
                        pointInTime.evidenceDataAsOf());
            }
        }
        return affected;
    }
}
