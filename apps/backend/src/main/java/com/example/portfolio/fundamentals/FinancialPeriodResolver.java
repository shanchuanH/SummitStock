package com.example.portfolio.fundamentals;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
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
            if (periodType == null || metric.isEmpty() || !conceptMapping.valid(metric.orElseThrow(), fact)) continue;
            groups.computeIfAbsent(new PeriodKey(periodType, fact.periodEnd()), ignored -> new ArrayList<>())
                    .add(new MappedFact(metric.orElseThrow(), fact));
        }
        return groups.entrySet().stream()
                .map(entry -> resolve(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(ResolvedPeriod::endDate))
                .toList();
    }

    private static ResolvedPeriod resolve(PeriodKey key, List<MappedFact> facts) {
        var candidates = new LinkedHashMap<FinancialMetric, List<ProviderModels.CompanyFact>>();
        for (var mapped : facts) {
            candidates
                    .computeIfAbsent(mapped.metric(), ignored -> new ArrayList<>())
                    .add(mapped.fact());
        }
        var observations = new LinkedHashMap<FinancialMetric, ProviderModels.CompanyFact>();
        var provenance = new LinkedHashMap<FinancialMetric, MetricProvenance>();
        for (var entry : candidates.entrySet()) {
            var resolved = entry.getKey() == FinancialMetric.TOTAL_DEBT
                    ? totalDebt(entry.getValue())
                    : single(entry.getValue());
            observations.put(entry.getKey(), resolved.fact());
            provenance.put(
                    entry.getKey(),
                    new MetricProvenance(
                            resolved.sourceConcepts(), "financial-concepts-v2", resolved.aggregationMethod()));
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
                Map.copyOf(observations),
                Map.copyOf(provenance));
    }

    private static ResolvedFact single(List<ProviderModels.CompanyFact> facts) {
        var fact = facts.stream()
                .reduce(FinancialPeriodResolver::latestRestatement)
                .orElseThrow();
        return new ResolvedFact(fact, List.of(fact.concept()), "SINGLE_CONCEPT");
    }

    private static ResolvedFact totalDebt(List<ProviderModels.CompanyFact> facts) {
        var latestByConcept = new LinkedHashMap<String, ProviderModels.CompanyFact>();
        for (var fact : facts) latestByConcept.merge(fact.concept(), fact, FinancialPeriodResolver::latestRestatement);
        var aggregate = List.of("DebtCurrentAndNoncurrent", "LongTermDebt").stream()
                .map(latestByConcept::get)
                .filter(java.util.Objects::nonNull)
                .reduce(FinancialPeriodResolver::latestRestatement);
        if (aggregate.isPresent()) {
            var fact = aggregate.orElseThrow();
            return new ResolvedFact(fact, List.of(fact.concept()), "PREFERRED_AGGREGATE");
        }
        var components = new ArrayList<ProviderModels.CompanyFact>();
        addIfPresent(components, latestByConcept.get("ShortTermBorrowings"));
        var currentDebt = latestByConcept.get("LongTermDebtCurrent") != null
                ? latestByConcept.get("LongTermDebtCurrent")
                : latestByConcept.get("LongTermDebtAndFinanceLeaseObligationsCurrent");
        addIfPresent(components, currentDebt);
        addIfPresent(components, latestByConcept.get("LongTermDebtNoncurrent"));
        if (components.isEmpty()) return single(facts);
        var base = components.stream()
                .reduce(FinancialPeriodResolver::latestRestatement)
                .orElseThrow();
        var sum = components.stream().map(ProviderModels.CompanyFact::value).reduce(BigDecimal.ZERO, BigDecimal::add);
        var synthetic = new ProviderModels.CompanyFact(
                base.businessMetric(),
                base.taxonomy(),
                "TOTAL_DEBT_COMPONENT_SUM",
                base.unit(),
                sum,
                base.periodStart(),
                base.periodEnd(),
                base.filingDate(),
                base.accessionNumber(),
                base.form(),
                base.sourceUri(),
                base.fiscalYear(),
                base.fiscalPeriod());
        return new ResolvedFact(
                synthetic,
                components.stream().map(ProviderModels.CompanyFact::concept).toList(),
                "SUM_DISTINCT_COMPONENTS");
    }

    private static void addIfPresent(List<ProviderModels.CompanyFact> values, ProviderModels.CompanyFact fact) {
        if (fact != null) values.add(fact);
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
            Map<FinancialMetric, ProviderModels.CompanyFact> facts,
            Map<FinancialMetric, MetricProvenance> metricProvenance) {
        public ResolvedPeriod(
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
            this(
                    fiscalYear,
                    fiscalQuarter,
                    periodType,
                    startDate,
                    endDate,
                    filedAt,
                    accessionNumber,
                    formType,
                    source,
                    quality,
                    facts,
                    Map.of());
        }

        public String canonicalFiscalPeriod() {
            return periodType == PeriodType.ANNUAL ? "FY" : fiscalQuarter == null ? null : "Q" + fiscalQuarter;
        }
    }

    public record MetricProvenance(List<String> sourceConcepts, String mappingVersion, String aggregationMethod) {
        public MetricProvenance {
            sourceConcepts = List.copyOf(sourceConcepts);
        }
    }

    private record PeriodKey(PeriodType periodType, LocalDate endDate) {}

    private record MappedFact(FinancialMetric metric, ProviderModels.CompanyFact fact) {}

    private record ResolvedFact(
            ProviderModels.CompanyFact fact, List<String> sourceConcepts, String aggregationMethod) {}
}
