package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ManualAnalysisRunIntegrationTest extends PortfolioImportIntegrationSupport {
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
                .isEqualTo(27);

        var repeatedResponse = request();
        assertThat(repeatedResponse.getResponse().getStatus()).isEqualTo(202);
        var repeated = json.readTree(repeatedResponse.getResponse().getContentAsString());
        assertThat(uuid(repeated, "runId")).isEqualTo(rerunId);
        assertThat(repeated.path("state").asString()).isEqualTo("RUNNING");
    }

    private org.springframework.test.web.servlet.MvcResult request() throws Exception {
        return mockMvc.perform(post("/api/v1/analysis/runs")
                        .with(httpBasic(EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"USER_REFRESH\"}"))
                .andReturn();
    }
}
