package com.example.portfolio.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinancialMetricEngineTest {
    @Test
    void derivesGrowthMarginsCashFlowLeverageAndDilution() {
        var current = FinancialTestFixtures.period(
                "2025-12-31",
                Map.ofEntries(
                        Map.entry(FinancialMetric.REVENUE, bd("120")),
                        Map.entry(FinancialMetric.OPERATING_INCOME, bd("24")),
                        Map.entry(FinancialMetric.NET_INCOME, bd("18")),
                        Map.entry(FinancialMetric.DILUTED_EPS, bd("6")),
                        Map.entry(FinancialMetric.OPERATING_CASH_FLOW, bd("22")),
                        Map.entry(FinancialMetric.CAPEX, bd("4")),
                        Map.entry(FinancialMetric.CASH, bd("40")),
                        Map.entry(FinancialMetric.TOTAL_DEBT, bd("10")),
                        Map.entry(FinancialMetric.CURRENT_ASSETS, bd("50")),
                        Map.entry(FinancialMetric.CURRENT_LIABILITIES, bd("25")),
                        Map.entry(FinancialMetric.DILUTED_SHARES, bd("102"))));
        var prior = FinancialTestFixtures.period(
                "2024-12-31",
                Map.of(
                        FinancialMetric.REVENUE, bd("100"),
                        FinancialMetric.DILUTED_EPS, bd("5"),
                        FinancialMetric.DILUTED_SHARES, bd("100")));
        var threeYears = FinancialTestFixtures.period("2022-12-31", Map.of(FinancialMetric.REVENUE, bd("80")));

        var result = new FinancialMetricEngine().compute(current, List.of(threeYears, prior, current));

        assertThat(result.values().get(FinancialMetric.REVENUE_YOY)).isEqualByComparingTo("0.2");
        assertThat(result.values().get(FinancialMetric.OPERATING_MARGIN)).isEqualByComparingTo("0.2");
        assertThat(result.values().get(FinancialMetric.FREE_CASH_FLOW)).isEqualByComparingTo("18");
        assertThat(result.values().get(FinancialMetric.NET_CASH)).isEqualByComparingTo("30");
        assertThat(result.values().get(FinancialMetric.CURRENT_RATIO)).isEqualByComparingTo("2");
        assertThat(result.values().get(FinancialMetric.SHARE_DILUTION_YOY)).isEqualByComparingTo("0.02");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
