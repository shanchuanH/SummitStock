package com.example.portfolio.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.infrastructure.PositionAnalystDataStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionReportEarningsAsOfTest {
    @Test
    void historicalCountdownUsesAnalysisCutoffInsteadOfReportOpenTime() {
        var decisionCutoff = Instant.parse("2026-08-01T20:00:00Z");
        var evidence = new PositionAnalystDataStore.EarningsData(
                LocalDateTime.parse("2026-08-11T20:00:00"),
                "AFTER_CLOSE",
                "MEDIUM",
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                "HOLD",
                "HEALTHY",
                LocalDateTime.parse("2026-07-31T12:00:00"));

        var report = PositionReportController.earnings(evidence, decisionCutoff);

        assertThat(report.daysUntilEarnings()).isEqualTo(10);
    }
}
