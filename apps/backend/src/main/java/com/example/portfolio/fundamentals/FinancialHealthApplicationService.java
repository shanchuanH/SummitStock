package com.example.portfolio.fundamentals;

import com.example.portfolio.analysis.application.StrategyDefinitionLoader;
import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.market.provider.ProviderModels;
import java.math.MathContext;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
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
            addTtmLeverage(values, rows);
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

    static void addTtmLeverage(
            EnumMap<FinancialMetric, java.math.BigDecimal> values,
            java.util.List<FinancialEvidenceStore.MetricPeriod> rows) {
        var netCash = values.get(FinancialMetric.NET_CASH);
        if (netCash == null || netCash.signum() >= 0) return;
        var quarterlyFcf = new LinkedHashMap<java.util.UUID, FinancialEvidenceStore.MetricPeriod>();
        rows.stream()
                .filter(row -> "QUARTERLY".equals(row.periodType()))
                .filter(row -> FinancialMetric.FREE_CASH_FLOW.name().equals(row.metricCode()))
                .sorted(Comparator.comparing(FinancialEvidenceStore.MetricPeriod::endDate)
                        .thenComparing(FinancialEvidenceStore.MetricPeriod::filedAt)
                        .reversed())
                .forEach(row -> quarterlyFcf.putIfAbsent(row.periodId(), row));
        var latestFour = quarterlyFcf.values().stream().limit(4).toList();
        if (latestFour.size() != 4) return;
        var ttmFcf = latestFour.stream()
                .map(FinancialEvidenceStore.MetricPeriod::value)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (ttmFcf.signum() > 0) {
            values.put(FinancialMetric.NET_DEBT_TO_FCF, netCash.negate().divide(ttmFcf, MathContext.DECIMAL128));
        }
    }
}
