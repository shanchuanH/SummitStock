package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailedStepBlocksDependentStepTest extends AnalysisRunDependencyTest {
    @Test
    void permanentFailureBlocksEveryDependentStep() {
        var runId = createRun();
        orchestrator.initialize(runId);
        var job = claimed(runId, "COLLECT_BARS");
        orchestrator.failed(job, "BAD_DATA");
        assertThat(status(runId, "COLLECT_BARS")).isEqualTo("FAILED");
        assertThat(status(runId, "VALIDATE_BARS")).isEqualTo("BLOCKED");
        assertThat(jdbc.sql("SELECT status FROM portfolio_analysis_run WHERE id=UUID_TO_BIN(:id)")
                        .param("id", runId.toString())
                        .query(String.class)
                        .single())
                .isEqualTo("FAILED");
    }
}
