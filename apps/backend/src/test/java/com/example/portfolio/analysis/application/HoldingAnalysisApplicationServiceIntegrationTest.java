package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HoldingAnalysisApplicationServiceIntegrationTest extends HoldingAnalysisIntegrationFixture {
    @Test
    void representativeHoldingsUseDifferentEvidencePoliciesAndTemplates() {
        var results = analysis.analyzeAll(USER_ID).stream()
                .collect(Collectors.toMap(value -> value.evidence().instrument().symbol(), Function.identity()));

        assertThat(results).containsOnlyKeys("GOOGL", "DRAM", "DXYZ");
        assertThat(results.get("GOOGL").analysis().readiness().name()).isEqualTo("READY");
        assertThat(results.get("GOOGL").analysis().recommendedAction()).isEqualTo(RecommendationAction.TRIM);
        assertThat(results.get("DRAM").analysis().recommendedAction()).isEqualTo(RecommendationAction.ADD);
        assertThat(results.get("DXYZ").analysis().recommendedAction()).isEqualTo(RecommendationAction.TRIM);
        assertThat(results.get("DXYZ").analysis().confidence()).isEqualTo("LOW");
        assertThat(results.values())
                .allSatisfy(value -> assertThat(value.analysis().configHash()).hasSize(64));
    }

    @Test
    void underweightAloneNeverTriggersAdd() {
        jdbc.sql(
                        """
                        INSERT INTO price_bar (id,instrument_id,timeframe,bar_start,market_date,open_price,high_price,
                          low_price,close_price,volume,adjusted,provider,source_timestamp,checksum,
                          normalization_version,quality_status,data_as_of,created_at)
                        VALUES (UUID_TO_BIN('97000000-0000-0000-0000-000000000004'),
                          UUID_TO_BIN('93000000-0000-0000-0000-000000000003'),'1D',:asOf,:marketDate,2,2,2,2,
                          1000,TRUE,'TEST_REVALUE',:asOf,SHA2('underweight',256),'v1','HEALTHY',:asOf,:asOf)
                        """)
                .param("asOf", clock.instant().plusSeconds(1))
                .param("marketDate", tradingCalendar.latestCompletedSession(clock.instant()))
                .update();
        positionMarks.captureForUser(USER_ID, clock.instant().plusSeconds(1));

        var result = analysis.analyze(USER_ID, DXYZ_POSITION);

        assertThat(result.analysis().currentWeight())
                .isLessThan(result.analysis().targetWeightMin());
        assertThat(result.resolution().suppressed())
                .extracting(RecommendationCandidate::action)
                .doesNotContain(RecommendationAction.ADD);
        assertThat(result.analysis().recommendedAction()).isNotEqualTo(RecommendationAction.ADD);
    }
}
