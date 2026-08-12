package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PipelineSemanticContractTest {
    @Test
    void pipelineNamesDescribeImplementedEffectsWithoutPretendingToGenerateArtifacts() {
        assertThat(AnalysisRunOrchestrator.PIPELINE)
                .contains("CHECK_ACTIVE_THESES", "UPDATE_DIP_EVENTS", "COUNT_ACTIVE_RECOMMENDATIONS")
                .doesNotContain("UPDATE_THESES_EVENTS", "GENERATE_DAILY_DIGEST");
    }
}
