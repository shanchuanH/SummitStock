package com.example.portfolio.fundamentals;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

final class FinancialTestFixtures {
    private FinancialTestFixtures() {}

    static FinancialConceptMapping mapping() {
        var values = new LinkedHashMap<String, FinancialMetric>();
        for (var metric : FinancialMetric.values()) values.put(metric.name() + "Concept", metric);
        return new FinancialConceptMapping(values);
    }

    static ProviderModels.CompanyFact fact(
            FinancialMetric metric, String value, String end, String filed, String accession, String form) {
        var endDate = LocalDate.parse(end);
        return new ProviderModels.CompanyFact(
                metric.name(),
                "us-gaap",
                metric.name() + "Concept",
                metric == FinancialMetric.DILUTED_EPS ? "USD/shares" : "USD",
                new BigDecimal(value),
                endDate.minusMonths(form.startsWith("10-K") ? 12 : 3),
                endDate,
                LocalDate.parse(filed),
                accession,
                form,
                "https://sec.test/" + accession);
    }

    static FinancialPeriodResolver.ResolvedPeriod period(String end, Map<FinancialMetric, BigDecimal> values) {
        var facts = new LinkedHashMap<FinancialMetric, ProviderModels.CompanyFact>();
        values.forEach((metric, value) -> facts.put(
                metric,
                fact(
                        metric,
                        value.toPlainString(),
                        end,
                        LocalDate.parse(end).plusMonths(2).toString(),
                        "acc-" + end,
                        "10-K")));
        var date = LocalDate.parse(end);
        return new FinancialPeriodResolver.ResolvedPeriod(
                date.getYear(),
                null,
                FinancialPeriodResolver.PeriodType.ANNUAL,
                date.minusYears(1),
                date,
                date.plusMonths(2),
                "acc-" + end,
                "10-K",
                "https://sec.test/acc-" + end,
                ProviderModels.QualityStatus.HEALTHY,
                Map.copyOf(facts));
    }
}
