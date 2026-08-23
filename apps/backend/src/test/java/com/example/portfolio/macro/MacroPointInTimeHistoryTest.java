package com.example.portfolio.macro;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MacroPointInTimeHistoryTest {
    @Test
    void selectsLatestObservationVersionAvailableAtTheReplayCutoff() {
        var versions = List.of(
                observation("2026-01-10", "10", "2026-01-10T21:00:00Z"),
                observation("2026-01-10", "20", "2026-01-20T21:00:00Z"));

        var jan15 = MacroApplicationService.selectHistoryVersions(versions, context("2026-01-15T23:59:59Z"));
        var jan25 = MacroApplicationService.selectHistoryVersions(versions, context("2026-01-25T23:59:59Z"));

        assertThat(jan15).containsExactly(new BigDecimal("10"));
        assertThat(jan25).containsExactly(new BigDecimal("20"));
    }

    @Test
    void excludesFutureObservationDatesAndEvidenceOlderThanTheFiveYearWindow() {
        var versions = List.of(
                observation("2020-12-31", "1", "2020-12-31T21:00:00Z"),
                observation("2026-01-10", "2", "2026-01-10T21:00:00Z"),
                observation("2026-01-16", "3", "2026-01-16T21:00:00Z"));

        var selected = MacroApplicationService.selectHistoryVersions(versions, context("2026-01-15T23:59:59Z"));

        assertThat(selected).containsExactly(new BigDecimal("2"));
    }

    private static DecisionAsOfContext context(String cutoff) {
        return new DecisionAsOfContext(LocalDate.parse("2026-01-15"), Instant.parse(cutoff), "3.0.0-draft");
    }

    private static MacroApplicationService.HistoryObservation observation(String date, String value, String asOf) {
        return new MacroApplicationService.HistoryObservation(
                LocalDate.parse(date), new BigDecimal(value), Instant.parse(asOf));
    }
}
