package com.example.portfolio.fundamentals;

import com.example.portfolio.analysis.application.StrategyDefinitionLoader;
import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.market.provider.ProviderModels;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class FinancialHealthApplicationService {
    private final FinancialEvidenceStore store;
    private final StrategyDefinitionLoader strategies;
    private final PortfolioProperties properties;
    private final FinancialHealthEngine engine = new FinancialHealthEngine();

    public FinancialHealthApplicationService(
            FinancialEvidenceStore store, StrategyDefinitionLoader strategies, PortfolioProperties properties) {
        this.store = store;
        this.strategies = strategies;
        this.properties = properties;
    }

    public int computeAll() {
        int affected = 0;
        var strategy = strategies.load(properties.strategyConfigPath());
        var configured = strategy.financialHealth();
        var policy = new FinancialHealthEngine.Policy(
                configured.revenueGrowthStrong(),
                configured.revenueGrowthHealthy(),
                configured.marginDeteriorationWarningPctPoints(),
                configured.fcfMarginHealthy(),
                configured.dilutionWarning(),
                configured.netDebtToFcfWarning());
        for (var instrument : store.eligibleInstruments()) {
            var rows = store.metricPeriods(instrument.id());
            if (rows.isEmpty()) continue;
            var latest = rows.stream()
                    .max(Comparator.comparing(FinancialEvidenceStore.MetricPeriod::endDate)
                            .thenComparing(FinancialEvidenceStore.MetricPeriod::filedAt))
                    .orElseThrow();
            var values = new EnumMap<FinancialMetric, java.math.BigDecimal>(FinancialMetric.class);
            rows.stream()
                    .filter(row -> row.periodId().equals(latest.periodId()))
                    .forEach(row -> values.put(FinancialMetric.valueOf(row.metricCode()), row.value()));
            var quality = rows.stream()
                            .filter(row -> row.periodId().equals(latest.periodId()))
                            .allMatch(row -> "HEALTHY".equals(row.quality()))
                    ? ProviderModels.QualityStatus.HEALTHY
                    : ProviderModels.QualityStatus.PARTIAL;
            var evidence = values.entrySet().stream()
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue().toPlainString())
                    .collect(Collectors.joining("|"));
            var result = engine.evaluate(
                    new FinancialMetricEngine.MetricResult(java.util.Map.copyOf(values), quality), policy);
            affected += store.saveHealth(
                    instrument.id(),
                    latest.periodId(),
                    strategy.version(),
                    strategy.configHash(),
                    result,
                    latest.filedAt(),
                    FinancialEvidenceStore.sha256(evidence + "|" + strategy.configHash()));
        }
        return affected;
    }
}
