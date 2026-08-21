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

    private static CanonicalFinancialAggregation.Observation quarter(String end, String value, String asOf) {
        return new CanonicalFinancialAggregation.Observation(
                "QUARTERLY", LocalDate.parse(end), "REVENUE", new BigDecimal(value), Instant.parse(asOf));
    }
}
