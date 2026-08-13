package com.example.portfolio.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FinancialHealthEngineTest {
    @Test
    void emitsExplainableStrongHealthWithoutACompositeScore() {
        var values = Map.of(
                FinancialMetric.REVENUE,
                bd("120"),
                FinancialMetric.REVENUE_YOY,
                bd("0.20"),
                FinancialMetric.OPERATING_INCOME,
                bd("24"),
                FinancialMetric.NET_INCOME,
                bd("18"),
                FinancialMetric.OPERATING_MARGIN,
                bd("0.20"),
                FinancialMetric.NET_MARGIN,
                bd("0.15"),
                FinancialMetric.FREE_CASH_FLOW,
                bd("18"),
                FinancialMetric.FCF_MARGIN,
                bd("0.15"),
                FinancialMetric.NET_CASH,
                bd("30"),
                FinancialMetric.SHARE_DILUTION_YOY,
                bd("0.01"));

        var result = new FinancialHealthEngine()
                .evaluate(
                        new FinancialMetricEngine.MetricResult(values, ProviderModels.QualityStatus.HEALTHY), policy());

        assertThat(result.overall()).isEqualTo(FinancialHealthEngine.Status.STRONG);
        assertThat(result.positives()).isNotEmpty();
        assertThat(result.negatives()).isEmpty();
    }

    @Test
    void positiveButMateriallyDeterioratingMarginIsNotHealthy() {
        var values = Map.ofEntries(
                Map.entry(FinancialMetric.REVENUE, bd("120")),
                Map.entry(FinancialMetric.REVENUE_YOY, bd("0.10")),
                Map.entry(FinancialMetric.OPERATING_INCOME, bd("18")),
                Map.entry(FinancialMetric.NET_INCOME, bd("12")),
                Map.entry(FinancialMetric.OPERATING_MARGIN, bd("0.15")),
                Map.entry(FinancialMetric.OPERATING_MARGIN_YOY_CHANGE, bd("-0.05")),
                Map.entry(FinancialMetric.NET_MARGIN, bd("0.10")),
                Map.entry(FinancialMetric.FREE_CASH_FLOW, bd("15")),
                Map.entry(FinancialMetric.FCF_MARGIN, bd("0.125")),
                Map.entry(FinancialMetric.NET_CASH, bd("20")),
                Map.entry(FinancialMetric.SHARE_DILUTION_YOY, bd("0.01")));

        var result = new FinancialHealthEngine()
                .evaluate(
                        new FinancialMetricEngine.MetricResult(values, ProviderModels.QualityStatus.HEALTHY), policy());

        assertThat(result.profitability()).isEqualTo(FinancialHealthEngine.Status.WEAKENING);
        assertThat(result.overall()).isNotEqualTo(FinancialHealthEngine.Status.HEALTHY);
        assertThat(result.negatives()).contains("Operating margin deteriorated materially year over year");
    }

    static FinancialHealthEngine.Policy policy() {
        return new FinancialHealthEngine.Policy(bd("0.15"), bd("0.05"), bd("0.03"), bd("0.10"), bd("0.03"), bd("3.0"));
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
