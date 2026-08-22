package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.portfolio.market.provider.TradingCalendar;
import com.example.portfolio.runtime.AnalysisRunOrchestrator;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class ManualAnalysisRunIntegrationTest extends PortfolioImportIntegrationSupport {
    @Autowired
    TradingCalendar tradingCalendar;

    @Test
    void authenticatedOwnerCanQueueOneImmediateRerunWithoutConcurrentDuplicates() throws Exception {
        var preview = preview("fidelity-positions.csv");
        var confirmation = confirm(uuid(preview, "batchId"), 0, "[{\"rowNumber\":7,\"ignored\":true}]");
        var originalRunId = uuid(confirmation, "analysisRunId");
        update("UPDATE portfolio_analysis_run SET status='SUCCEEDED' WHERE id=UUID_TO_BIN('" + originalRunId + "')");

        var firstResponse = request();
        assertThat(firstResponse.getResponse().getStatus()).isEqualTo(202);
        var first = json.readTree(firstResponse.getResponse().getContentAsString());
        var rerunId = uuid(first, "runId");
        assertThat(rerunId).isNotEqualTo(originalRunId);
        assertThat(first.path("state").asString()).isEqualTo("STARTING");
        assertThat(count("SELECT COUNT(*) FROM portfolio_analysis_step WHERE run_id=UUID_TO_BIN('" + rerunId + "')"))
                .isEqualTo(AnalysisRunOrchestrator.PIPELINE.size());
        assertThat(count("SELECT COUNT(*) FROM portfolio_analysis_step_dependency WHERE run_id=UUID_TO_BIN('" + rerunId
                        + "')"))
                .isEqualTo(AnalysisRunOrchestrator.canonicalDependencyCount());
        assertThat(count("SELECT COUNT(*) FROM job_run WHERE job_type='PORTFOLIO_ANALYSIS' "
                        + "AND analysis_run_id=UUID_TO_BIN('" + rerunId + "')"))
                .isEqualTo(1);
        var persisted = jdbc.sql(
                        "SELECT market_date marketDate,decision_cutoff decisionCutoff FROM portfolio_analysis_run WHERE id=UUID_TO_BIN(:id)")
                .param("id", rerunId.toString())
                .query(RunCutoff.class)
                .single();
        assertThat(persisted.decisionCutoff().toInstant(ZoneOffset.UTC))
                .isEqualTo(tradingCalendar.sessionClose(persisted.marketDate()));

        var repeatedResponse = request();
        assertThat(repeatedResponse.getResponse().getStatus()).isEqualTo(202);
        var repeated = json.readTree(repeatedResponse.getResponse().getContentAsString());
        assertThat(uuid(repeated, "runId")).isEqualTo(rerunId);
        assertThat(repeated.path("state").asString()).isEqualTo("RUNNING");
    }

    @Test
    void stalledRunIsAbandonedAndReplacedWithAPollableNewRun() throws Exception {
        var confirmation =
                confirm(uuid(preview("fidelity-positions.csv"), "batchId"), 0, "[{\"rowNumber\":7,\"ignored\":true}]");
        var stalledRunId = uuid(confirmation, "analysisRunId");
        update(
                "UPDATE portfolio_analysis_run SET status='RUNNING',updated_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 5 MINUTE) "
                        + "WHERE id=UUID_TO_BIN('" + stalledRunId + "')");
        update("UPDATE portfolio_analysis_step SET updated_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 5 MINUTE) "
                + "WHERE run_id=UUID_TO_BIN('" + stalledRunId + "')");

        var response = request();
        var body = json.readTree(response.getResponse().getContentAsString());
        var replacement = uuid(body, "runId");

        assertThat(replacement).isNotEqualTo(stalledRunId);
        assertThat(count("SELECT COUNT(*) FROM portfolio_analysis_run WHERE id=UUID_TO_BIN('" + stalledRunId
                        + "') AND status='FAILED' AND error_code='STALLED_ABANDONED'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM job_run WHERE analysis_run_id=UUID_TO_BIN('" + stalledRunId
                        + "') AND status='DEAD'"))
                .isGreaterThan(0);
        assertThat(count("SELECT COUNT(*) FROM portfolio_analysis_run WHERE id=UUID_TO_BIN('" + replacement
                        + "') AND status='RUNNING'"))
                .isEqualTo(1);
    }

    @Test
    void activeStepProgressPreventsAHealthyRunFromBeingAbandoned() throws Exception {
        var confirmation =
                confirm(uuid(preview("fidelity-positions.csv"), "batchId"), 0, "[{\"rowNumber\":7,\"ignored\":true}]");
        var activeRunId = uuid(confirmation, "analysisRunId");
        update(
                "UPDATE portfolio_analysis_run SET status='RUNNING',updated_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 5 MINUTE) "
                        + "WHERE id=UUID_TO_BIN('" + activeRunId + "')");
        update("UPDATE portfolio_analysis_step SET updated_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 5 MINUTE) "
                + "WHERE run_id=UUID_TO_BIN('" + activeRunId + "')");
        update("UPDATE portfolio_analysis_step SET updated_at=UTC_TIMESTAMP(6) " + "WHERE run_id=UUID_TO_BIN('"
                + activeRunId + "') AND step_type='PORTFOLIO_ANALYSIS'");

        var response = request();
        var body = json.readTree(response.getResponse().getContentAsString());

        assertThat(uuid(body, "runId")).isEqualTo(activeRunId);
        assertThat(count("SELECT COUNT(*) FROM portfolio_analysis_run WHERE id=UUID_TO_BIN('" + activeRunId
                        + "') AND status='RUNNING' AND error_code IS NULL"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM portfolio_analysis_run r JOIN app_user u ON u.id=r.user_id "
                        + "WHERE u.email='" + EMAIL + "' AND r.status IN ('QUEUED','RUNNING','WAITING')"))
                .isEqualTo(1);
    }

    private org.springframework.test.web.servlet.MvcResult request() throws Exception {
        return mockMvc.perform(post("/api/v1/analysis/runs")
                        .with(httpBasic(EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_REFRESH\"}"))
                .andReturn();
    }

    private record RunCutoff(LocalDate marketDate, LocalDateTime decisionCutoff) {}
}
