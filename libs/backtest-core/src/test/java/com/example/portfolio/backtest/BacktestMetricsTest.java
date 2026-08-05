package com.example.portfolio.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class BacktestMetricsTest {
    @Test
    void reportsAllRequiredDecisionMetrics() {
        var observations = List.of(
                new BacktestMetrics.Observation(
                        true, true, true, true, true, .01, .03, .08, true, false, false, true, true),
                new BacktestMetrics.Observation(
                        false, true, false, false, false, -.01, .01, .02, true, true, true, false, true));
        var report = new BacktestMetrics().aggregate(observations);
        assertThat(report.regimeAccuracy()).isEqualTo(.5);
        assertThat(report.stopProtectionRate()).isEqualTo(.5);
        assertThat(report.etfSuccessRate()).isEqualTo(1);
        assertThat(report.recommendationReturn5()).isZero();
        assertThat(report.urgencyPrecision()).isEqualTo(.5);
        assertThat(report.holdFrequency()).isEqualTo(.5);
        assertThat(report.quantityCompliance()).isEqualTo(.5);
        assertThat(report.accountabilityCoverage()).isEqualTo(1);
    }
}
