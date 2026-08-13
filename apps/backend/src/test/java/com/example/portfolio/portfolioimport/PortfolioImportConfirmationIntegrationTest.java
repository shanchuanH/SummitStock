package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

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
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM position WHERE import_source='FIDELITY_CSV' AND classification_confirmed=TRUE"))
                .isEqualTo(3);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM portfolio_cash_setup WHERE location_code='IN_FIDELITY' AND confirmed_total=14000"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM cash_bucket WHERE bucket_type='EMERGENCY' AND current_amount=14000"))
                .isEqualTo(1);
        mockMvc.perform(get("/api/v1/analysis/status/{runId}", uuid(confirmed, "analysisRunId"))
                        .with(httpBasic(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANALYSIS_RUNNING"))
                .andExpect(jsonPath("$.stages.length()").value(8));
    }

    @Test
    void confirmationFailsUntilClassificationAndSafetyCashAreExplicit() throws Exception {
        var value = preview("fidelity-positions-updated.csv");
        var batchId = uuid(value, "batchId");

        mockMvc.perform(
                        post("/api/v1/portfolio-imports/{batchId}/confirm", batchId)
                                .with(httpBasic(EMAIL, PASSWORD))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"expectedVersion":0,"accountMappings":[],"rowOverrides":[],
                                 "cashSetup":{"location":"IN_FIDELITY","externalEmergencyAmount":"0"}}
                                """))
                .andExpect(status().isUnprocessableContent());

        assertThat(count("SELECT COUNT(*) FROM position WHERE import_source='FIDELITY_CSV'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM portfolio_cash_setup")).isZero();
    }
}
