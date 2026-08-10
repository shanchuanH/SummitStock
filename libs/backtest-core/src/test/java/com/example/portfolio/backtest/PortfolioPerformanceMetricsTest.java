package com.example.portfolio.backtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PortfolioPerformanceMetricsTest {
    @Test
    void calculatesGovernanceMetricsFromChronologicalOutOfSampleCurve() {
        var start = LocalDate.parse("2026-01-02");
        var report = new PortfolioPerformanceMetrics()
                .aggregate(List.of(
                        new PortfolioPerformanceMetrics.Observation(start, 100, 0, 0, .5, 0),
                        new PortfolioPerformanceMetrics.Observation(start.plusDays(1), 110, .1, 1, .8, .02),
                        new PortfolioPerformanceMetrics.Observation(start.plusDays(2), 99, .2, -1, .6, -.01),
                        new PortfolioPerformanceMetrics.Observation(start.plusDays(3), 120, .1, 2, .7, .01)));
        assertThat(report.maxDrawdown()).isCloseTo(.1, within(1e-12));
        assertThat(report.turnover()).isCloseTo(.1, within(1e-12));
        assertThat(report.averageR()).isEqualTo(.5);
        assertThat(report.timeUnderwaterSessions()).isEqualTo(1);
        assertThat(report.exposure()).isCloseTo(.65, within(1e-12));
        assertThat(report.benchmarkRelativeReturn()).isCloseTo(.180102, within(1e-6));
    }
}
