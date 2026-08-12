package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.runtime.AnalysisRunOrchestrator;
import com.example.portfolio.runtime.DurableJobCoordinator;
import com.example.portfolio.runtime.DurableJobStore;
import com.example.portfolio.runtime.JobHandlerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class EodPipelineVerticalIntegrationTest extends PortfolioImportIntegrationSupport {
    @Autowired
    DurableJobStore jobs;

    @Autowired
    JobHandlerRegistry handlers;

    @Autowired
    AnalysisRunOrchestrator orchestrator;

    @Autowired
    MeterRegistry meters;

    @Autowired
    Clock clock;

    @Test
    void importedFixtureExecutesToRecommendationsThroughRealHandlers() throws Exception {
        var preview = preview("fidelity-positions.csv");
        var confirmed = confirm(uuid(preview, "batchId"), 0, "[{\"rowNumber\":7,\"ignored\":true}]");
        var runId = uuid(confirmed, "analysisRunId");
        var coordinator = new DurableJobCoordinator(jobs, handlers, orchestrator, meters, clock);

        for (int iteration = 0; iteration < 40 && !terminal(runId); iteration++) {
            assertThat(coordinator.runOnce())
                    .as("pipeline iteration " + iteration)
                    .isTrue();
        }

        assertThat(runStatus(runId)).withFailMessage(() -> diagnostics(runId)).isEqualTo("SUCCEEDED");
        assertThat(count("SELECT COUNT(*) FROM price_bar")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM indicator_snapshot")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM market_regime_snapshot")).isPositive();
        assertThat(count("SELECT COUNT(*) FROM portfolio_drawdown_snapshot d JOIN app_user u ON u.id=d.user_id "
                        + "WHERE u.email='" + EMAIL + "'"))
                .isPositive();
        assertThat(count("SELECT COUNT(*) FROM holding_analysis_snapshot h JOIN position p ON p.id=h.position_id "
                        + "JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id "
                        + "WHERE u.email='" + EMAIL + "' AND p.status='OPEN' "
                        + "AND h.analysis_run_id=UUID_TO_BIN('" + runId + "') AND h.decision_payload IS NOT NULL"))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM recommendation r JOIN app_user u ON u.id=r.user_id " + "WHERE u.email='"
                        + EMAIL + "' AND r.status='ACTIVE'"))
                .isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM recommendation r "
                        + "JOIN holding_analysis_snapshot h ON h.id=r.holding_analysis_id "
                        + "JOIN app_user u ON u.id=r.user_id WHERE u.email='" + EMAIL + "' "
                        + "AND h.analysis_run_id=UUID_TO_BIN('" + runId + "')"))
                .isEqualTo(3);
    }

    private boolean terminal(java.util.UUID runId) {
        return java.util.Set.of("SUCCEEDED", "FAILED", "BLOCKED").contains(runStatus(runId));
    }

    private String runStatus(java.util.UUID runId) {
        return jdbc.sql("SELECT status FROM portfolio_analysis_run WHERE id=UUID_TO_BIN(:id)")
                .param("id", runId.toString())
                .query(String.class)
                .single();
    }

    private String diagnostics(java.util.UUID runId) {
        return jdbc.sql(
                        """
                        SELECT CONCAT(s.step_type,':',s.status,':',COALESCE(CAST(j.result_json AS CHAR),''),
                          ':',COALESCE(s.error_code,''))
                        FROM portfolio_analysis_step s LEFT JOIN job_run j
                          ON j.analysis_run_id=s.run_id AND j.job_type=s.step_type
                        WHERE s.run_id=UUID_TO_BIN(:runId) ORDER BY s.created_at
                        """)
                .param("runId", runId.toString())
                .query(String.class)
                .list()
                .toString();
    }
}
