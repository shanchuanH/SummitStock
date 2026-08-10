package com.example.portfolio.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RestatementResolutionTest {
    @Test
    void latestFiledObservationWinsForTheSameCanonicalPeriod() {
        var original = FinancialTestFixtures.fact(
                FinancialMetric.REVENUE, "100", "2025-12-31", "2026-02-01", "original", "10-K");
        var restated = FinancialTestFixtures.fact(
                FinancialMetric.REVENUE, "108", "2025-12-31", "2026-04-01", "restated", "10-K/A");

        var period = new FinancialPeriodResolver()
                .resolve(List.of(original, restated), FinancialTestFixtures.mapping())
                .getFirst();

        assertThat(period.facts().get(FinancialMetric.REVENUE).value()).isEqualByComparingTo("108");
        assertThat(period.accessionNumber()).isEqualTo("restated");
    }
}
