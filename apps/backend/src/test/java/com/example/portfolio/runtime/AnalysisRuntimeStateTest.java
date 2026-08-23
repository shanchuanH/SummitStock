package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AnalysisRuntimeStateTest {
    @Test
    void queuedRunReportsOfflineWorkerInsteadOfInfiniteWaiting() {
        assertThat(AnalysisStatusController.externalState("QUEUED", false, 3L)).isEqualTo("WORKER_OFFLINE");
    }

    @Test
    void oldActiveRunReportsStalledOnlyWhenWorkerIsAlive() {
        assertThat(AnalysisStatusController.externalState("RUNNING", true, 120L))
                .isEqualTo("STALLED");
        assertThat(AnalysisStatusController.externalState("RUNNING", false, 120L))
                .isEqualTo("WORKER_OFFLINE");
    }

    @Test
    void terminalStatesRemainExplicit() {
        assertThat(AnalysisStatusController.externalState("PARTIAL", true, null))
                .isEqualTo("PARTIAL");
        assertThat(AnalysisStatusController.externalState("FAILED", true, null)).isEqualTo("FAILED");
        assertThat(AnalysisStatusController.externalState("BLOCKED", true, null))
                .isEqualTo("BLOCKED");
        assertThat(AnalysisStatusController.externalState("SUCCEEDED", true, null))
                .isEqualTo("READY");
    }
}
