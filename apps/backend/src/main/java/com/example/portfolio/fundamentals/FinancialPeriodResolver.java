package com.example.portfolio.fundamentals;

import com.example.portfolio.market.provider.ProviderModels;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FinancialPeriodResolver {
    public List<ResolvedPeriod> resolve(
            List<ProviderModels.CompanyFact> facts, FinancialConceptMapping conceptMapping) {
        var groups = new LinkedHashMap<PeriodKey, List<MappedFact>>();
        for (var fact : facts) {
            var periodType = periodType(fact.form());
            var metric = conceptMapping.resolve(fact.taxonomy(), fact.concept());
            if (periodType == null || metric.isEmpty()) continue;
            groups.computeIfAbsent(new PeriodKey(periodType, fact.periodEnd()), ignored -> new ArrayList<>())
                    .add(new MappedFact(metric.orElseThrow(), fact));
        }
        return groups.entrySet().stream()
                .map(entry -> resolve(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ResolvedPeriod::endDate))
                .toList();
    }

    private static ResolvedPeriod resolve(PeriodKey key, List<MappedFact> facts) {
        var observations = new LinkedHashMap<FinancialMetric, ProviderModels.CompanyFact>();
        for (var mapped : facts) {
            observations.merge(mapped.metric(), mapped.fact(), FinancialPeriodResolver::latestRestatement);
        }
        var latest = observations.values().stream()
                .max(Comparator.comparing(ProviderModels.CompanyFact::filingDate)
                        .thenComparing(ProviderModels.CompanyFact::accessionNumber))
                .orElseThrow();
        var start = observations.values().stream()
                .map(ProviderModels.CompanyFact::periodStart)
                .filter(java.util.Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);
        var fiscalYear = latest.fiscalYear() == null ? key.endDate().getYear() : latest.fiscalYear();
        var quarter = key.periodType() == PeriodType.ANNUAL ? null : fiscalQuarter(latest.fiscalPeriod());
        var quality =
                observations.size() >= 5 ? ProviderModels.QualityStatus.HEALTHY : ProviderModels.QualityStatus.PARTIAL;
        return new ResolvedPeriod(
                fiscalYear,
                quarter,
                key.periodType(),
                start,
                key.endDate(),
                latest.filingDate(),
                latest.accessionNumber(),
                latest.form(),
                latest.sourceUri(),
                quality,
                Map.copyOf(observations));
    }

    private static Integer fiscalQuarter(String value) {
        if (value == null) return null;
        return switch (value.strip().toUpperCase(java.util.Locale.ROOT)) {
            case "Q1" -> 1;
            case "Q2" -> 2;
            case "Q3" -> 3;
            case "Q4" -> 4;
            default -> null;
        };
    }

    private static ProviderModels.CompanyFact latestRestatement(
            ProviderModels.CompanyFact left, ProviderModels.CompanyFact right) {
        var comparison = left.filingDate().compareTo(right.filingDate());
        if (comparison == 0) comparison = left.accessionNumber().compareTo(right.accessionNumber());
        return comparison >= 0 ? left : right;
    }

    private static PeriodType periodType(String form) {
        if (form == null) return null;
        var normalized = form.toUpperCase(java.util.Locale.ROOT);
        if (normalized.startsWith("10-K") || normalized.startsWith("20-F") || normalized.startsWith("40-F")) {
            return PeriodType.ANNUAL;
        }
        if (normalized.startsWith("10-Q")) return PeriodType.QUARTERLY;
        return null;
    }

    public enum PeriodType {
        QUARTERLY,
        ANNUAL
    }

    public record ResolvedPeriod(
            int fiscalYear,
            Integer fiscalQuarter,
            PeriodType periodType,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate filedAt,
            String accessionNumber,
            String formType,
            String source,
            ProviderModels.QualityStatus quality,
            Map<FinancialMetric, ProviderModels.CompanyFact> facts) {
        public String canonicalFiscalPeriod() {
            return periodType == PeriodType.ANNUAL ? "FY" : fiscalQuarter == null ? null : "Q" + fiscalQuarter;
        }
    }

    private record PeriodKey(PeriodType periodType, LocalDate endDate) {}

    private record MappedFact(FinancialMetric metric, ProviderModels.CompanyFact fact) {}
}
