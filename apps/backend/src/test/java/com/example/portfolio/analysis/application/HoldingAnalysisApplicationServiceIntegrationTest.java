package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
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
        assertThat(results.get("DXYZ").analysis().recommendedAction()).isEqualTo(RecommendationAction.WATCH);
        assertThat(results.get("DXYZ").analysis().confidence()).isEqualTo("LOW");
        assertThat(results.values())
                .allSatisfy(value -> assertThat(value.analysis().configHash()).hasSize(64));
    }
}
