package com.example.portfolio.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
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
}
