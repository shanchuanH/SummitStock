package com.example.portfolio.valuation;

import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.estimates.EstimateRevisionEngine;
import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ValuationApplicationService {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private final ValuationEvidenceStore store;
    private final PortfolioProperties properties;
    private final ValuationBootstrapService bootstrap;
    private final Clock clock;
    private final ValuationEngineV2 engine = new ValuationEngineV2();

    public ValuationApplicationService(
            ValuationEvidenceStore store,
            PortfolioProperties properties,
            ValuationBootstrapService bootstrap,
            Clock clock) {
        this.store = store;
        this.properties = properties;
        this.bootstrap = bootstrap;
        this.clock = clock;
    }

    public int computeAll() {
        return computeAll(new DecisionAsOfContext(LocalDate.now(clock), clock.instant(), properties.strategyVersion()));
    }

    public int computeAll(UUID analysisRunId) {
        return computeAll(store.analysisContext(analysisRunId));
    }

    public int computeAll(DecisionAsOfContext context) {
        int affected = bootstrap.bootstrap(context);
        var configHash = store.configHash(context.strategyVersion());
        for (var input : store.inputs(context)) {
            if (input.marketDate() == null || input.price() == null || input.shares() == null) continue;
            var metrics = metrics(input);
            var quality = ValuationEngineV2.availableFamilyCount(metrics) >= 2
                    ? ProviderModels.QualityStatus.HEALTHY
                    : ProviderModels.QualityStatus.PARTIAL;
            store.saveMetrics(input.instrumentId(), input.marketDate(), metrics, quality.name(), context.dataCutoff());
            var history3y = store.history(
                    input.instrumentId(), input.marketDate().minusYears(3), context.marketDate(), context.dataCutoff());
            var history5y = store.history(
                    input.instrumentId(), input.marketDate().minusYears(5), context.marketDate(), context.dataCutoff());
            var assessment = engine.assess(new ValuationEngineV2.Input(
                    metrics, history3y, history5y, health(input.health()), revision(input.revision()), quality));
            var growthAdjusted = ratio(metrics.forwardPe(), input.revenueGrowth());
            affected += store.saveAssessment(
                    input.instrumentId(),
                    assessment,
                    growthAdjusted,
                    context.strategyVersion(),
                    configHash,
                    context.dataCutoff());
        }
        return affected;
    }

    static ValuationEngineV2.Metrics metrics(ValuationEvidenceStore.InputRow input) {
        var marketCap = input.price().multiply(input.shares(), MATH);
        var enterpriseValue = marketCap.add(zero(input.totalDebt())).subtract(zero(input.cash()));
        return new ValuationEngineV2.Metrics(
                ratio(input.price(), input.trailingEps()),
                ratio(input.price(), input.forwardEps()),
                ratio(enterpriseValue, input.revenue()),
                ratio(input.freeCashFlow(), marketCap),
                ratio(marketCap, input.revenue()),
                marketCap);
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        return numerator == null || denominator == null || denominator.signum() <= 0
                ? null
                : numerator.divide(denominator, MATH);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static ValuationEngineV2.CompanyHealth health(String value) {
        try {
            return ValuationEngineV2.CompanyHealth.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ValuationEngineV2.CompanyHealth.MISSING;
        }
    }

    private static EstimateRevisionEngine.RevisionState revision(String value) {
        try {
            return EstimateRevisionEngine.RevisionState.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return EstimateRevisionEngine.RevisionState.MISSING;
        }
    }
}
