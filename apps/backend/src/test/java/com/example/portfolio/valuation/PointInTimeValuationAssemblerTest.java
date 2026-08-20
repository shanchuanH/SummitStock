package com.example.portfolio.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PointInTimeValuationAssemblerTest {
    @Test
    void excludesFutureRestatementsAndFutureEstimateRevisions() {
        var metrics = new ArrayList<PointInTimeValuationAssembler.MetricPoint>();
        metrics.add(metric("QUARTERLY", "2022-09-30", "DILUTED_EPS", "1", "2022-11-01T00:00:00Z"));
        metrics.add(metric("QUARTERLY", "2022-12-31", "DILUTED_EPS", "2", "2023-02-01T00:00:00Z"));
        metrics.add(metric("QUARTERLY", "2023-03-31", "DILUTED_EPS", "3", "2023-05-01T00:00:00Z"));
        metrics.add(metric("QUARTERLY", "2023-06-30", "DILUTED_EPS", "4", "2023-07-31T00:00:00Z"));
        metrics.add(metric("QUARTERLY", "2022-12-31", "DILUTED_EPS", "200", "2026-02-01T00:00:00Z"));
        metrics.add(metric("QUARTERLY", "2023-06-30", "COMMON_SHARES_OUTSTANDING", "98", "2023-07-31T00:00:00Z"));
        metrics.add(metric("QUARTERLY", "2023-06-30", "COMMON_SHARES_OUTSTANDING", "120", "2026-02-01T00:00:00Z"));
        var estimates = List.of(
                estimate("2024-12-31", "8", "2023-07-15T00:00:00Z"),
                estimate("2024-12-31", "12", "2026-01-01T00:00:00Z"));

        var result = new PointInTimeValuationAssembler().assemble(LocalDate.parse("2023-08-01"), metrics, estimates);

        assertThat(result.trailingEps()).isEqualByComparingTo("10");
        assertThat(result.forwardEps()).isEqualByComparingTo("8");
        assertThat(result.commonShares()).isEqualByComparingTo("98");
    }

    @Test
    void missingEvidenceRemainsMissing() {
        var result = new PointInTimeValuationAssembler().assemble(LocalDate.parse("2023-08-01"), List.of(), List.of());

        assertThat(result.trailingEps()).isNull();
        assertThat(result.forwardEps()).isNull();
        assertThat(result.commonShares()).isNull();
    }

    private static PointInTimeValuationAssembler.MetricPoint metric(
            String type, String end, String code, String value, String asOf) {
        return new PointInTimeValuationAssembler.MetricPoint(
                type, LocalDate.parse(end), code, new BigDecimal(value), Instant.parse(asOf));
    }

    private static PointInTimeValuationAssembler.EstimatePoint estimate(String end, String value, String asOf) {
        return new PointInTimeValuationAssembler.EstimatePoint(
                LocalDate.parse(end), new BigDecimal(value), Instant.parse(asOf));
    }
}
