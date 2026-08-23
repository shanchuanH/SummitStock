package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class HoldingAnalysisApplicationServiceIntegrationTest extends HoldingAnalysisIntegrationFixture {
    @Test
    void replayFailsClosedWhenLegacyRunHasNoPersistedDecisionCutoff() {
        var runId = java.util.UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO portfolio_analysis_run
                          (id,user_id,market_date,strategy_version,status,run_key,created_at,updated_at,version)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),CURRENT_DATE,'3.0.0-draft','SUCCEEDED',
                          :runKey,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)
                        """)
                .param("id", runId.toString())
                .param("userId", USER_ID.toString())
                .param("runKey", "legacy-no-cutoff:" + runId)
                .update();

        assertThatThrownBy(() -> analysis.analyzeAll(USER_ID, runId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Analysis run decision cutoff is unavailable");
    }

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

    @Test
    void canonicalEarningsEvidenceKeepsEventRiskSeparateFromPolicyAction() {
        jdbc.sql(
                        """
                        INSERT INTO earnings_risk_snapshot
                          (id,position_id,strategy_version,event_count,event_risk,next_event_at,action,rule_ids,
                           evidence_checksum,data_as_of,valid_until,created_at)
                        VALUES (UUID_TO_BIN('99600000-0000-0000-0000-000000000001'),
                          UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),'3.0.0-draft',4,'EXTREME',
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 2 DAY),'REDUCE_HALF',JSON_ARRAY('EARNINGS.TEST'),
                          SHA2('earnings-risk-separation',256),UTC_TIMESTAMP(6),
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 7 DAY),UTC_TIMESTAMP(6))
                        """)
                .update();

        var event = evidenceAssembler.assemble(USER_ID, GOOGL_POSITION).nextEvent();

        assertThat(event.available()).isTrue();
        assertThat(event.eventRisk()).isEqualTo("EXTREME");
        assertThat(event.policyAction()).isEqualTo("REDUCE_HALF");
    }
}
