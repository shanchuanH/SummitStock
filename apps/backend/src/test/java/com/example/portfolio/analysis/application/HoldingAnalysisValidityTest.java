package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.market.provider.UsEquityTradingCalendar;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class HoldingAnalysisValidityTest {
    private final UsEquityTradingCalendar calendar = new UsEquityTradingCalendar();

    @Test
    void fridayEvidenceRemainsValidThroughTheNextTradingSession() {
        var fridayClose = DecisionAsOfContext.marketClose(LocalDate.parse("2026-08-21"), "3.0.0-draft");

        assertThat(HoldingAnalysisApplicationService.validUntil(fridayClose, calendar))
                .isEqualTo(Instant.parse("2026-08-24T20:00:00Z"));
    }

    @Test
    void holidayDoesNotShortenValidityToTwentyFourWallClockHours() {
        var preLaborDayClose = DecisionAsOfContext.marketClose(LocalDate.parse("2026-09-04"), "3.0.0-draft");

        assertThat(HoldingAnalysisApplicationService.validUntil(preLaborDayClose, calendar))
                .isEqualTo(Instant.parse("2026-09-08T20:00:00Z"));
    }
}
