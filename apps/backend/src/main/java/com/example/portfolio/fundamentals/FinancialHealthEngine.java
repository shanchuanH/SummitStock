package com.example.portfolio.fundamentals;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FinancialHealthEngine {
    public HealthResult evaluate(FinancialMetricEngine.MetricResult metrics, Policy policy) {
        var values = metrics.values();
        if (!hasMinimumEvidence(values)) return HealthResult.missing();

        var positives = new ArrayList<String>();
        var negatives = new ArrayList<String>();
        var growth = growth(values, policy, positives, negatives);
        var profitability = profitability(values, positives, negatives);
        var cashFlow = cashFlow(values, policy, positives, negatives);
        var balanceSheet = balanceSheet(values, policy, positives, negatives);
        var dilution = dilution(values, policy, positives, negatives);

        var revenueGrowth = values.get(FinancialMetric.REVENUE_YOY);
        var fcf = values.get(FinancialMetric.FREE_CASH_FLOW);
        var overall = Status.STABLE;
        if ((revenueGrowth != null && revenueGrowth.compareTo(new BigDecimal("-0.20")) < 0)
                || (fcf != null && fcf.signum() < 0 && balanceSheet == Status.WEAKENING)) {
            overall = Status.BROKEN;
        } else if (List.of(growth, profitability, cashFlow, balanceSheet, dilution).stream()
                        .filter(value -> value == Status.WEAKENING || value == Status.BROKEN)
                        .count()
                >= 2) {
            overall = Status.WEAKENING;
        } else if (growth == Status.STRONG
                && cashFlow == Status.HEALTHY
                && balanceSheet != Status.WEAKENING
                && dilution != Status.WEAKENING) {
            overall = Status.STRONG;
        } else if (growth == Status.HEALTHY && profitability != Status.WEAKENING && cashFlow != Status.WEAKENING) {
            overall = Status.HEALTHY;
        }
        return new HealthResult(
                overall,
                growth,
                profitability,
                cashFlow,
                balanceSheet,
                dilution,
                positives,
                negatives,
                metrics.quality());
    }

    private static Status growth(
            Map<FinancialMetric, BigDecimal> values, Policy policy, List<String> positives, List<String> negatives) {
        var value = values.get(FinancialMetric.REVENUE_YOY);
        if (value == null) return Status.MISSING;
        if (value.compareTo(policy.revenueGrowthStrong()) >= 0) {
            positives.add("Revenue growth is strong");
            return Status.STRONG;
        }
        if (value.compareTo(policy.revenueGrowthHealthy()) >= 0) {
            positives.add("Revenue growth is healthy");
            return Status.HEALTHY;
        }
        if (value.signum() < 0) {
            negatives.add("Revenue is contracting");
            return Status.WEAKENING;
        }
        return Status.STABLE;
    }

    private static Status profitability(
            Map<FinancialMetric, BigDecimal> values, List<String> positives, List<String> negatives) {
        var operatingMargin = values.get(FinancialMetric.OPERATING_MARGIN);
        var netMargin = values.get(FinancialMetric.NET_MARGIN);
        if (operatingMargin == null || netMargin == null) return Status.MISSING;
        if (operatingMargin.signum() < 0 || netMargin.signum() < 0) {
            negatives.add("Profitability is negative");
            return Status.WEAKENING;
        }
        positives.add("Operations and net income are profitable");
        return Status.HEALTHY;
    }

    private static Status cashFlow(
            Map<FinancialMetric, BigDecimal> values, Policy policy, List<String> positives, List<String> negatives) {
        var fcf = values.get(FinancialMetric.FREE_CASH_FLOW);
        var margin = values.get(FinancialMetric.FCF_MARGIN);
        if (fcf == null || margin == null) return Status.MISSING;
        if (fcf.signum() < 0) {
            negatives.add("Free cash flow is negative");
            return Status.WEAKENING;
        }
        if (margin.compareTo(policy.fcfMarginHealthy()) >= 0) {
            positives.add("Free-cash-flow margin is healthy");
            return Status.HEALTHY;
        }
        return Status.STABLE;
    }

    private static Status balanceSheet(
            Map<FinancialMetric, BigDecimal> values, Policy policy, List<String> positives, List<String> negatives) {
        var netCash = values.get(FinancialMetric.NET_CASH);
        var leverage = values.get(FinancialMetric.NET_DEBT_TO_FCF);
        if (netCash == null) return Status.MISSING;
        if (netCash.signum() >= 0) {
            positives.add("Balance sheet is in a net-cash position");
            return Status.HEALTHY;
        }
        if (leverage == null || leverage.compareTo(policy.netDebtToFcfWarning()) > 0) {
            negatives.add("Net debt is high relative to free cash flow");
            return Status.WEAKENING;
        }
        return Status.STABLE;
    }

    private static Status dilution(
            Map<FinancialMetric, BigDecimal> values, Policy policy, List<String> positives, List<String> negatives) {
        var value = values.get(FinancialMetric.SHARE_DILUTION_YOY);
        if (value == null) return Status.MISSING;
        if (value.compareTo(policy.dilutionWarning()) > 0) {
            negatives.add("Diluted share count is rising materially");
            return Status.WEAKENING;
        }
        positives.add("Share dilution is controlled");
        return Status.HEALTHY;
    }

    private static boolean hasMinimumEvidence(Map<FinancialMetric, BigDecimal> values) {
        return values.containsKey(FinancialMetric.REVENUE)
                && values.containsKey(FinancialMetric.OPERATING_INCOME)
                && values.containsKey(FinancialMetric.NET_INCOME)
                && values.containsKey(FinancialMetric.FREE_CASH_FLOW)
                && values.containsKey(FinancialMetric.NET_CASH);
    }

    public enum Status {
        STRONG,
        HEALTHY,
        STABLE,
        WEAKENING,
        BROKEN,
        MISSING
    }

    public record Policy(
            BigDecimal revenueGrowthStrong,
            BigDecimal revenueGrowthHealthy,
            BigDecimal marginDeteriorationWarningPctPoints,
            BigDecimal fcfMarginHealthy,
            BigDecimal dilutionWarning,
            BigDecimal netDebtToFcfWarning) {}

    public record HealthResult(
            Status overall,
            Status growth,
            Status profitability,
            Status cashFlow,
            Status balanceSheet,
            Status dilution,
            List<String> positives,
            List<String> negatives,
            ProviderModels.QualityStatus quality) {
        public HealthResult {
            positives = List.copyOf(positives);
            negatives = List.copyOf(negatives);
        }

        static HealthResult missing() {
            return new HealthResult(
                    Status.MISSING,
                    Status.MISSING,
                    Status.MISSING,
                    Status.MISSING,
                    Status.MISSING,
                    Status.MISSING,
                    List.of(),
                    List.of("Required financial facts are missing"),
                    ProviderModels.QualityStatus.MISSING);
        }
    }
}
