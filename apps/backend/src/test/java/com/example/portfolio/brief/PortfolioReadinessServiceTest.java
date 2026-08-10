package com.example.portfolio.brief;

import static com.example.portfolio.analysis.domain.PortfolioAnalysisState.ANALYSIS_QUEUED;
import static com.example.portfolio.analysis.domain.PortfolioAnalysisState.ANALYSIS_READY;
import static com.example.portfolio.analysis.domain.PortfolioAnalysisState.FAILED;
import static com.example.portfolio.analysis.domain.PortfolioAnalysisState.NO_PORTFOLIO;
import static com.example.portfolio.analysis.domain.PortfolioAnalysisState.PARTIAL_ANALYSIS;
import static com.example.portfolio.analysis.domain.PortfolioAnalysisState.WAIT_FOR_MARKET_DATA;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortfolioReadinessServiceTest {
    private final PortfolioReadinessService service = new PortfolioReadinessService();

    @Test
    void coversRequiredReadinessStatesWithoutInferringSuccess() {
        assertThat(service.assess(facts(0, false, false, 0, 0, 0, 0, false))).isEqualTo(NO_PORTFOLIO);
        assertThat(service.assess(facts(2, true, true, 0, 0, 2, 0, false))).isEqualTo(ANALYSIS_QUEUED);
        assertThat(service.assess(facts(2, true, false, 1, 0, 2, 0, false))).isEqualTo(WAIT_FOR_MARKET_DATA);
        assertThat(service.assess(facts(2, true, false, 0, 1, 1, 0, false))).isEqualTo(PARTIAL_ANALYSIS);
        assertThat(service.assess(facts(2, true, false, 0, 0, 2, 0, false))).isEqualTo(ANALYSIS_READY);
        assertThat(service.assess(facts(2, true, false, 0, 0, 0, 0, true))).isEqualTo(FAILED);
    }

    @Test
    void aReadyPortfolioMayHaveActionsOrNoActionsWithoutChangingReadiness() {
        var ready = service.assess(facts(1, true, false, 0, 0, 1, 0, false));
        assertThat(ready).isEqualTo(ANALYSIS_READY);
        assertThat(ExecutiveBriefQueryService.headline(ready, 2, 1)).contains("2 item(s) require action");
        assertThat(ExecutiveBriefQueryService.headline(ready, 0, 0)).isEqualTo("NO URGENT ACTION");
        assertThat(ExecutiveBriefQueryService.headline(PARTIAL_ANALYSIS, 0, 0)).doesNotContain("NO URGENT ACTION");
    }

    private static PortfolioReadinessService.ReadinessFacts facts(
            long open,
            boolean hasRun,
            boolean queued,
            long missingMarket,
            long missingFundamentals,
            long analyzed,
            long stale,
            boolean failed) {
        return new PortfolioReadinessService.ReadinessFacts(
                open,
                false,
                false,
                hasRun,
                queued,
                missingMarket,
                missingFundamentals,
                missingFundamentals,
                analyzed,
                stale,
                false,
                failed);
    }
}
