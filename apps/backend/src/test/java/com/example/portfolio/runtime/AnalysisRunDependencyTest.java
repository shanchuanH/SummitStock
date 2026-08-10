package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class AnalysisRunDependencyTest extends MySqlIntegrationTest {
    @Autowired
    AnalysisRunOrchestrator orchestrator;

    @Autowired
    JdbcClient jdbc;

    @Test
    void completionQueuesOnlyTheDirectDependentStep() {
        var runId = createRun();
        orchestrator.initialize(runId);
        var job = claimed(runId, "PORTFOLIO_ANALYSIS");
        orchestrator.started(job);
        orchestrator.succeeded(job, JobExecutionResult.succeeded("{}", Instant.now()));
        assertThat(status(runId, "PORTFOLIO_ANALYSIS")).isEqualTo("SUCCEEDED");
        assertThat(status(runId, "COLLECT_QUOTES")).isEqualTo("QUEUED");
        assertThat(status(runId, "COLLECT_BARS")).isEqualTo("PENDING");
        assertThat(jdbc.sql("SELECT COUNT(*) FROM job_run WHERE analysis_run_id=UUID_TO_BIN(:id)")
                        .param("id", runId.toString())
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    UUID createRun() {
        var userId = UUID.randomUUID();
        var runId = UUID.randomUUID();
        jdbc.sql("INSERT INTO app_user VALUES(UUID_TO_BIN(:id),:email,'x','ACTIVE','UTC',:now,:now,0)")
                .param("id", userId.toString())
                .param("email", userId + "@example.local")
                .param("now", Instant.now())
                .update();
        jdbc.sql(
                        """
                INSERT INTO portfolio_analysis_run (
                    id,user_id,market_date,strategy_version,status,run_key,created_at,updated_at,version
                ) VALUES(UUID_TO_BIN(:id),UUID_TO_BIN(:user),:date,'1.0.0-draft','QUEUED',:key,:now,:now,0)
                """)
                .param("id", runId.toString())
                .param("user", userId.toString())
                .param("date", LocalDate.now())
                .param("key", "test:" + runId)
                .param("now", Instant.now())
                .update();
        return runId;
    }

    String status(UUID runId, String step) {
        return jdbc.sql("SELECT status FROM portfolio_analysis_step WHERE run_id=UUID_TO_BIN(:id) AND step_type=:step")
                .param("id", runId.toString())
                .param("step", step)
                .query(String.class)
                .single();
    }

    static DurableJobStore.ClaimedJob claimed(UUID runId, String type) {
        return new DurableJobStore.ClaimedJob(
                UUID.randomUUID(), UUID.randomUUID(), runId, type, "key", "{}", 1, 5, Instant.now(), "test");
    }
}
