package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PipelineDagContractTest {
    @Test
    void independentProviderBranchesStartAfterTheRootAndJoinBeforeDecisioning() {
        assertThat(AnalysisRunOrchestrator.PIPELINE).hasSize(27).doesNotHaveDuplicates();
        assertThat(AnalysisRunOrchestrator.DEPENDENCIES.keySet())
                .containsExactlyInAnyOrderElementsOf(AnalysisRunOrchestrator.PIPELINE.subList(1, 27));
        assertThat(AnalysisRunOrchestrator.canonicalDependencyCount()).isPositive();
        assertThat(AnalysisRunOrchestrator.DEPENDENCIES.get("COLLECT_BARS")).containsExactly("PORTFOLIO_ANALYSIS");
        assertThat(AnalysisRunOrchestrator.DEPENDENCIES.get("COLLECT_FUNDAMENTALS"))
                .containsExactly("CHECK_FILINGS");
        assertThat(AnalysisRunOrchestrator.DEPENDENCIES.get("COLLECT_ESTIMATES"))
                .containsExactly("PORTFOLIO_ANALYSIS");
        assertThat(AnalysisRunOrchestrator.DEPENDENCIES.get("COLLECT_MACRO")).containsExactly("PORTFOLIO_ANALYSIS");
        assertThat(AnalysisRunOrchestrator.DEPENDENCIES.get("COMPUTE_HOLDING_ANALYSIS"))
                .contains("COMPUTE_VALUATION", "COMPUTE_REGIME", "RECALCULATE_STOPS", "UPDATE_DIP_EVENTS");
        assertThat(AnalysisRunOrchestrator.DEPENDENCIES.get("GENERATE_RECOMMENDATIONS"))
                .containsExactly("COMPUTE_HOLDING_ANALYSIS");
    }
}
