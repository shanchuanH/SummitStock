package com.example.portfolio.fundamentals;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class FinancialMetricEngine {
    private static final MathContext MATH = MathContext.DECIMAL128;

    public MetricResult compute(
            FinancialPeriodResolver.ResolvedPeriod current, List<FinancialPeriodResolver.ResolvedPeriod> history) {
        var values = new EnumMap<FinancialMetric, BigDecimal>(FinancialMetric.class);
        current.facts().forEach((metric, fact) -> values.put(metric, fact.value()));
        deriveCurrent(values);
        var prior = comparable(current, history, 1);
        var threeYears = comparable(current, history, 3);
        growth(values, FinancialMetric.REVENUE_YOY, FinancialMetric.REVENUE, prior);
        growth(values, FinancialMetric.EPS_YOY, FinancialMetric.DILUTED_EPS, prior);
        growth(values, FinancialMetric.SHARE_DILUTION_YOY, FinancialMetric.DILUTED_SHARES, prior);
        cagr(values, FinancialMetric.REVENUE_3Y_CAGR, FinancialMetric.REVENUE, threeYears, 3);
        var quality = required(values)
                ? com.example.portfolio.market.provider.ProviderModels.QualityStatus.HEALTHY
                : com.example.portfolio.market.provider.ProviderModels.QualityStatus.PARTIAL;
        return new MetricResult(Map.copyOf(values), quality);
    }

    private static void deriveCurrent(EnumMap<FinancialMetric, BigDecimal> values) {
        var ocf = values.get(FinancialMetric.OPERATING_CASH_FLOW);
        var capex = values.get(FinancialMetric.CAPEX);
        if (ocf != null && capex != null) values.put(FinancialMetric.FREE_CASH_FLOW, ocf.subtract(capex.abs()));
        ratio(values, FinancialMetric.OPERATING_MARGIN, FinancialMetric.OPERATING_INCOME, FinancialMetric.REVENUE);
        ratio(values, FinancialMetric.NET_MARGIN, FinancialMetric.NET_INCOME, FinancialMetric.REVENUE);
        ratio(values, FinancialMetric.FCF_MARGIN, FinancialMetric.FREE_CASH_FLOW, FinancialMetric.REVENUE);
        ratio(values, FinancialMetric.FCF_CONVERSION, FinancialMetric.FREE_CASH_FLOW, FinancialMetric.NET_INCOME);
        ratio(
                values,
                FinancialMetric.CURRENT_RATIO,
                FinancialMetric.CURRENT_ASSETS,
                FinancialMetric.CURRENT_LIABILITIES);
        var cash = values.get(FinancialMetric.CASH);
        var debt = values.get(FinancialMetric.TOTAL_DEBT);
        if (cash != null && debt != null) {
            var netCash = cash.subtract(debt);
            values.put(FinancialMetric.NET_CASH, netCash);
            var fcf = values.get(FinancialMetric.FREE_CASH_FLOW);
            if (fcf != null && fcf.signum() > 0 && netCash.signum() < 0) {
                values.put(FinancialMetric.NET_DEBT_TO_FCF, netCash.negate().divide(fcf, MATH));
            }
        }
    }

    private static void ratio(
            EnumMap<FinancialMetric, BigDecimal> values,
            FinancialMetric output,
            FinancialMetric numerator,
            FinancialMetric denominator) {
        var top = values.get(numerator);
        var bottom = values.get(denominator);
        if (top != null && bottom != null && bottom.signum() != 0) values.put(output, top.divide(bottom, MATH));
    }

    private static Map<FinancialMetric, BigDecimal> comparable(
            FinancialPeriodResolver.ResolvedPeriod current,
            List<FinancialPeriodResolver.ResolvedPeriod> history,
            int years) {
        return history.stream()
                .filter(value -> value.periodType() == current.periodType())
                .filter(value -> value.fiscalYear() == current.fiscalYear() - years)
                .filter(value ->
                        java.util.Objects.equals(value.canonicalFiscalPeriod(), current.canonicalFiscalPeriod()))
                .sorted(java.util.Comparator.comparing(FinancialPeriodResolver.ResolvedPeriod::filedAt)
                        .reversed())
                .findFirst()
                .map(value -> {
                    var result = new EnumMap<FinancialMetric, BigDecimal>(FinancialMetric.class);
                    value.facts().forEach((metric, fact) -> result.put(metric, fact.value()));
                    return Map.copyOf(result);
                })
                .orElse(Map.of());
    }

    private static void growth(
            EnumMap<FinancialMetric, BigDecimal> values,
            FinancialMetric output,
            FinancialMetric input,
            Map<FinancialMetric, BigDecimal> prior) {
        var current = values.get(input);
        var previous = prior.get(input);
        if (current != null && previous != null && previous.signum() != 0) {
            values.put(output, current.divide(previous, MATH).subtract(BigDecimal.ONE));
        }
    }

    private static void cagr(
            EnumMap<FinancialMetric, BigDecimal> values,
            FinancialMetric output,
            FinancialMetric input,
            Map<FinancialMetric, BigDecimal> prior,
            int years) {
        var current = values.get(input);
        var previous = prior.get(input);
        if (current != null && previous != null && current.signum() > 0 && previous.signum() > 0) {
            var rate = Math.pow(current.divide(previous, MATH).doubleValue(), 1.0 / years) - 1.0;
            values.put(output, BigDecimal.valueOf(rate));
        }
    }

    private static boolean required(Map<FinancialMetric, BigDecimal> values) {
        return values.containsKey(FinancialMetric.REVENUE)
                && values.containsKey(FinancialMetric.OPERATING_INCOME)
                && values.containsKey(FinancialMetric.NET_INCOME)
                && values.containsKey(FinancialMetric.OPERATING_CASH_FLOW)
                && values.containsKey(FinancialMetric.CASH)
                && values.containsKey(FinancialMetric.TOTAL_DEBT);
    }

    public record MetricResult(
            Map<FinancialMetric, BigDecimal> values,
            com.example.portfolio.market.provider.ProviderModels.QualityStatus quality) {}
}
