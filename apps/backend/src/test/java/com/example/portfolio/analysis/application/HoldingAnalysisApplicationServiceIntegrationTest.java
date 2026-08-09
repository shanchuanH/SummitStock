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
        assertThat(results.get("DRAM").analysis().recommendedAction()).isEqualTo(RecommendationAction.HOLD);
        assertThat(results.get("DXYZ").analysis().recommendedAction()).isEqualTo(RecommendationAction.TRIM);
        assertThat(results.get("DXYZ").analysis().confidence()).isEqualTo("LOW");
        assertThat(results.values())
                .allSatisfy(value -> assertThat(value.analysis().configHash()).hasSize(64));
    }

    @Test
    void underweightAloneNeverTriggersAdd() {
        jdbc.sql("UPDATE position SET market_value=100 WHERE id=UUID_TO_BIN('94000000-0000-0000-0000-000000000003')")
                .update();

        var result = analysis.analyze(USER_ID, DXYZ_POSITION);

        assertThat(result.analysis().currentWeight())
                .isLessThan(result.analysis().targetWeightMin());
        assertThat(result.resolution().suppressed())
                .extracting(RecommendationCandidate::action)
                .doesNotContain(RecommendationAction.ADD);
        assertThat(result.analysis().recommendedAction()).isNotEqualTo(RecommendationAction.ADD);
    }
}
