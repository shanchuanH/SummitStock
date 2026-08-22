package com.example.portfolio.financialaggregation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalFinancialAggregationTest {
    @Test
    void aggregatesLatestKnownVersionOfFourLatestValidQuarters() {
        var cutoff = Instant.parse("2026-08-20T23:59:59Z");
        var values = List.of(
                quarter("2025-09-30", "1", "2025-11-01T00:00:00Z"),
                quarter("2025-12-31", "2", "2026-02-01T00:00:00Z"),
                quarter("2026-03-31", "3", "2026-05-01T00:00:00Z"),
                quarter("2026-06-30", "4", "2026-08-01T00:00:00Z"),
                quarter("2026-06-30", "40", "2026-08-21T00:00:00Z"));

        var result = CanonicalFinancialAggregation.ttm("REVENUE", cutoff, values);

        assertThat(result.value()).isEqualByComparingTo("10");
        assertThat(result.dataAsOf()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    }

    @Test
    void refusesFakeTtmWhenFewerThanFourQuartersExist() {
        var result = CanonicalFinancialAggregation.ttm(
                "REVENUE",
                Instant.parse("2026-08-20T23:59:59Z"),
                List.of(
                        quarter("2026-03-31", "3", "2026-05-01T00:00:00Z"),
                        quarter("2026-06-30", "4", "2026-08-01T00:00:00Z")));

        assertThat(result).isNull();
    }

    @Test
    void refusesToBridgeAMissingMiddleQuarterWithAnOlderQuarter() {
        var result = CanonicalFinancialAggregation.ttm(
                "REVENUE",
                Instant.parse("2026-08-20T23:59:59Z"),
                List.of(
                        fiscalQuarter("2026-06-30", 2026, 2, "4", "HEALTHY", "2026-08-01T00:00:00Z"),
                        fiscalQuarter("2026-03-31", 2026, 1, "3", "HEALTHY", "2026-05-01T00:00:00Z"),
                        fiscalQuarter("2025-09-30", 2025, 3, "1", "HEALTHY", "2025-11-01T00:00:00Z"),
                        fiscalQuarter("2025-06-30", 2025, 2, "9", "HEALTHY", "2025-08-01T00:00:00Z")));

        assertThat(result).isNull();
    }

    @Test
    void usesLatestRestatementKnownByCutoffAndExcludesFutureRestatement() {
        var cutoff = Instant.parse("2026-08-20T23:59:59Z");
        var result = CanonicalFinancialAggregation.ttm(
                "REVENUE",
                cutoff,
                List.of(
                        fiscalQuarter("2025-09-30", 2025, 3, "1", "HEALTHY", "2025-11-01T00:00:00Z"),
                        fiscalQuarter("2025-12-31", 2025, 4, "2", "HEALTHY", "2026-02-01T00:00:00Z"),
                        fiscalQuarter("2025-12-31", 2025, 4, "20", "HEALTHY", "2026-03-01T00:00:00Z"),
                        fiscalQuarter("2026-03-31", 2026, 1, "3", "HEALTHY", "2026-05-01T00:00:00Z"),
                        fiscalQuarter("2026-06-30", 2026, 2, "4", "HEALTHY", "2026-08-01T00:00:00Z"),
                        fiscalQuarter("2026-06-30", 2026, 2, "400", "HEALTHY", "2026-08-21T00:00:00Z")));

        assertThat(result.value()).isEqualByComparingTo("28");
        assertThat(result.dataAsOf()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    }

    @Test
    void invalidQuarterQualityMakesTtmUnavailable() {
        var result = CanonicalFinancialAggregation.ttm(
                "REVENUE",
                Instant.parse("2026-08-20T23:59:59Z"),
                List.of(
                        fiscalQuarter("2025-09-30", 2025, 3, "1", "HEALTHY", "2025-11-01T00:00:00Z"),
                        fiscalQuarter("2025-12-31", 2025, 4, "2", "PARTIAL", "2026-02-01T00:00:00Z"),
                        fiscalQuarter("2026-03-31", 2026, 1, "3", "HEALTHY", "2026-05-01T00:00:00Z"),
                        fiscalQuarter("2026-06-30", 2026, 2, "4", "HEALTHY", "2026-08-01T00:00:00Z")));

        assertThat(result).isNull();
    }

    @Test
    void fiscalYearBoundaryUsesFiscalMetadataRatherThanCalendarQuarter() {
        var result = CanonicalFinancialAggregation.ttm(
                "REVENUE",
                Instant.parse("2026-08-20T23:59:59Z"),
                List.of(
                        fiscalQuarter("2025-08-31", 2025, 4, "1", "HEALTHY", "2025-10-01T00:00:00Z"),
                        fiscalQuarter("2025-11-30", 2026, 1, "2", "HEALTHY", "2026-01-01T00:00:00Z"),
                        fiscalQuarter("2026-02-28", 2026, 2, "3", "HEALTHY", "2026-04-01T00:00:00Z"),
                        fiscalQuarter("2026-05-31", 2026, 3, "4", "HEALTHY", "2026-07-01T00:00:00Z")));

        assertThat(result.value()).isEqualByComparingTo("10");
    }

    private static CanonicalFinancialAggregation.Observation quarter(String end, String value, String asOf) {
        return new CanonicalFinancialAggregation.Observation(
                "QUARTERLY", LocalDate.parse(end), "REVENUE", new BigDecimal(value), Instant.parse(asOf));
    }

    private static CanonicalFinancialAggregation.Observation fiscalQuarter(
            String end, int fiscalYear, int fiscalQuarter, String value, String quality, String asOf) {
        return new CanonicalFinancialAggregation.Observation(
                "QUARTERLY",
                LocalDate.parse(end),
                fiscalYear,
                fiscalQuarter,
                "REVENUE",
                new BigDecimal(value),
                quality,
                quality,
                Instant.parse(asOf));
    }
}
