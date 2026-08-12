package com.example.portfolio.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinancialPeriodResolverTest {
    @Test
    void separatesAnnualAndQuarterlyPeriodsAndPreservesProvenance() {
        var annual = FinancialTestFixtures.fact(
                FinancialMetric.REVENUE, "100", "2025-12-31", "2026-02-01", "annual-1", "10-K");
        var quarter = FinancialTestFixtures.fact(
                FinancialMetric.REVENUE, "30", "2026-03-31", "2026-05-01", "quarter-1", "10-Q");

        var periods = new FinancialPeriodResolver().resolve(List.of(annual, quarter), FinancialTestFixtures.mapping());

        assertThat(periods).hasSize(2);
        assertThat(periods.getFirst().periodType()).isEqualTo(FinancialPeriodResolver.PeriodType.ANNUAL);
        assertThat(periods.getLast().fiscalQuarter()).isEqualTo(1);
        assertThat(periods.getLast().facts().get(FinancialMetric.REVENUE).accessionNumber())
                .isEqualTo("quarter-1");
    }

    @Test
    void usesProviderFiscalMetadataInsteadOfCalendarMonth() {
        var retailerQ1 = FinancialTestFixtures.factWithFiscal(
                FinancialMetric.REVENUE, "30", "2026-05-02", "2026-06-01", "retailer-q1", "10-Q", 2026, "Q1");

        var period = new FinancialPeriodResolver()
                .resolve(List.of(retailerQ1), FinancialTestFixtures.mapping())
                .getFirst();

        assertThat(period.fiscalYear()).isEqualTo(2026);
        assertThat(period.fiscalQuarter()).isEqualTo(1);
        assertThat(period.canonicalFiscalPeriod()).isEqualTo("Q1");
    }

    @Test
    void missingProviderQuarterDoesNotFallBackToCalendarQuarter() {
        var fact = FinancialTestFixtures.fact(
                FinancialMetric.REVENUE, "30", "2026-05-02", "2026-06-01", "unknown-quarter", "10-Q");
        fact = new com.example.portfolio.market.provider.ProviderModels.CompanyFact(
                fact.businessMetric(),
                fact.taxonomy(),
                fact.concept(),
                fact.unit(),
                fact.value(),
                fact.periodStart(),
                fact.periodEnd(),
                fact.filingDate(),
                fact.accessionNumber(),
                fact.form(),
                fact.sourceUri(),
                2026,
                null);

        var period = new FinancialPeriodResolver()
                .resolve(List.of(fact), FinancialTestFixtures.mapping())
                .getFirst();

        assertThat(period.fiscalQuarter()).isNull();
        assertThat(period.canonicalFiscalPeriod()).isNull();
    }

    @Test
    void totalDebtUsesAggregateWithoutDoubleCountingComponents() {
        var facts = List.of(
                debt("DebtCurrentAndNoncurrent", "90", "USD"),
                debt("LongTermDebtCurrent", "20", "USD"),
                debt("LongTermDebtNoncurrent", "70", "USD"));

        var period = new FinancialPeriodResolver().resolve(facts, debtMapping()).getFirst();

        assertThat(period.facts().get(FinancialMetric.TOTAL_DEBT).value()).isEqualByComparingTo("90");
        assertThat(period.metricProvenance().get(FinancialMetric.TOTAL_DEBT).sourceConcepts())
                .containsExactly("DebtCurrentAndNoncurrent");
        assertThat(period.metricProvenance().get(FinancialMetric.TOTAL_DEBT).aggregationMethod())
                .isEqualTo("PREFERRED_AGGREGATE");
    }

    @Test
    void totalDebtSumsDistinctComponentsAndRejectsInvalidUnitsAndSigns() {
        var facts = List.of(
                debt("ShortTermBorrowings", "10", "USD"),
                debt("LongTermDebtCurrent", "20", "USD"),
                debt("LongTermDebtAndFinanceLeaseObligationsCurrent", "999", "USD"),
                debt("LongTermDebtNoncurrent", "70", "USD"),
                debt("DebtCurrentAndNoncurrent", "1000000", "shares"),
                debt("LongTermDebt", "-5", "USD"));

        var period = new FinancialPeriodResolver().resolve(facts, debtMapping()).getFirst();

        assertThat(period.facts().get(FinancialMetric.TOTAL_DEBT).value()).isEqualByComparingTo("100");
        assertThat(period.metricProvenance().get(FinancialMetric.TOTAL_DEBT).sourceConcepts())
                .containsExactly("ShortTermBorrowings", "LongTermDebtCurrent", "LongTermDebtNoncurrent");
        assertThat(period.metricProvenance().get(FinancialMetric.TOTAL_DEBT).aggregationMethod())
                .isEqualTo("SUM_DISTINCT_COMPONENTS");
    }

    private static FinancialConceptMapping debtMapping() {
        return new FinancialConceptMapping(Map.of(
                "DebtCurrentAndNoncurrent", FinancialMetric.TOTAL_DEBT,
                "LongTermDebt", FinancialMetric.TOTAL_DEBT,
                "ShortTermBorrowings", FinancialMetric.TOTAL_DEBT,
                "LongTermDebtCurrent", FinancialMetric.TOTAL_DEBT,
                "LongTermDebtNoncurrent", FinancialMetric.TOTAL_DEBT,
                "LongTermDebtAndFinanceLeaseObligationsCurrent", FinancialMetric.TOTAL_DEBT));
    }

    private static ProviderModels.CompanyFact debt(String concept, String value, String unit) {
        return new ProviderModels.CompanyFact(
                "TOTAL_DEBT",
                "us-gaap",
                concept,
                unit,
                new BigDecimal(value),
                null,
                java.time.LocalDate.parse("2025-12-31"),
                java.time.LocalDate.parse("2026-02-01"),
                "debt-1",
                "10-K",
                "https://sec.test/debt-1",
                2025,
                "FY");
    }
}
