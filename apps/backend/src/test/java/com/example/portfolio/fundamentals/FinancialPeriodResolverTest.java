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
}
