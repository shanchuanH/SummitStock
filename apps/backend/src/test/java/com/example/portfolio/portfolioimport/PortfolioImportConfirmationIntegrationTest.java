package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortfolioImportConfirmationIntegrationTest extends PortfolioImportIntegrationSupport {
    @Test
    void confirmationReconcilesOnceAndQueuesDurableAnalysis() throws Exception {
        var preview = preview("fidelity-positions.csv");
        var batchId = uuid(preview, "batchId");
        var override = "[{\"rowNumber\":7,\"ignored\":true}]";

        var confirmed = confirm(batchId, 0, override);
        var replay = confirm(batchId, 0, override);

        assertThat(confirmed.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("version").asLong()).isEqualTo(2);
        assertThat(confirmed.get("analysisState").asString()).isEqualTo("ANALYSIS_QUEUED");
        assertThat(confirmed.get("openPositionCount").asInt()).isEqualTo(3);
        assertThat(confirmed.get("cashRowCount").asInt()).isEqualTo(1);
        assertThat(confirmed.get("compensationRowCount").asInt()).isEqualTo(1);
        assertThat(confirmed.get("idempotentReplay").asBoolean()).isFalse();
        assertThat(replay.get("idempotentReplay").asBoolean()).isTrue();
        assertThat(uuid(replay, "analysisRunId")).isEqualTo(uuid(confirmed, "analysisRunId"));
        assertThat(count("SELECT COUNT(*) FROM position_snapshot")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM compensation_holding")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM job_run WHERE job_type='PORTFOLIO_ANALYSIS'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE event_type='PORTFOLIO_IMPORT_CONFIRMED'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM position WHERE import_source='FIDELITY_CSV' AND average_cost IS NULL"))
                .isEqualTo(1);
    }
}
