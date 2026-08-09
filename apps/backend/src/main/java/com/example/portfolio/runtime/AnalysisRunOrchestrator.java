package com.example.portfolio.runtime;

import com.example.portfolio.configuration.PortfolioProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalysisRunOrchestrator {
    public static final List<String> PIPELINE = List.of(
            "PORTFOLIO_ANALYSIS",
            "COLLECT_QUOTES",
            "COLLECT_BARS",
            "VALIDATE_BARS",
            "COLLECT_CORPORATE_ACTIONS",
            "CHECK_FILINGS",
            "COLLECT_FUNDAMENTALS",
            "NORMALIZE_FINANCIALS",
            "COMPUTE_FINANCIAL_HEALTH",
            "COLLECT_ESTIMATES",
            "COMPUTE_REVISIONS",
            "COMPUTE_INDICATORS",
            "COLLECT_BREADTH_MACRO",
            "COMPUTE_REGIME",
            "SYNC_PORTFOLIO",
            "COMPUTE_DRAWDOWN_SOURCE",
            "RECALCULATE_STOPS",
            "UPDATE_THESES_EVENTS",
            "COMPUTE_HOLDING_ANALYSIS",
            "UPDATE_DIP_EVENTS",
            "GENERATE_RECOMMENDATIONS",
            "GENERATE_DAILY_DIGEST");

    private final JdbcClient jdbc;
    private final DurableJobStore jobs;
    private final Clock clock;

    public AnalysisRunOrchestrator(JdbcClient jdbc, DurableJobStore jobs, Clock clock) {
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.clock = clock;
    }

    @Transactional
    public void initialize(UUID runId) {
        for (int index = 0; index < PIPELINE.size(); index++) {
            var step = PIPELINE.get(index);
            var dependency = index == 0 ? null : PIPELINE.get(index - 1);
            jdbc.sql(
                            """
                            INSERT IGNORE INTO portfolio_analysis_step (
                                id, run_id, step_type, status, depends_on, attempts, created_at, updated_at, version
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:runId), :step, :status, :dependency, 0, :now, :now, 0
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("runId", runId.toString())
                    .param("step", step)
                    .param("status", index == 0 ? "QUEUED" : "PENDING")
                    .param("dependency", dependency)
                    .param("now", clock.instant())
                    .update();
        }
        jdbc.sql(
                        """
                        UPDATE portfolio_analysis_run
                        SET status='RUNNING', started_at=COALESCE(started_at,:now), updated_at=:now, version=version+1
                        WHERE id=UUID_TO_BIN(:runId) AND status IN ('QUEUED','WAITING','RUNNING')
                        """)
                .param("now", clock.instant())
                .param("runId", runId.toString())
                .update();
    }

    @Transactional
    public int scheduleForAllUsers(LocalDate marketDate, PortfolioProperties properties) {
        var users = jdbc.sql(
                        """
                        SELECT DISTINCT BIN_TO_UUID(u.id)
                        FROM app_user u JOIN investment_account a ON a.user_id=u.id
                        WHERE u.status='ACTIVE' AND a.active=TRUE
                        """)
                .query(UUID.class)
                .list();
        int scheduled = 0;
        for (var userId : users) {
            var key = "eod:" + userId + ":" + marketDate + ":" + properties.strategyVersion();
            var proposed = UUID.randomUUID();
            jdbc.sql(
                            """
                            INSERT IGNORE INTO portfolio_analysis_run (
                                id, user_id, import_batch_id, market_date, strategy_version, status,
                                run_key, created_at, updated_at, version
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:userId), NULL, :marketDate, :strategyVersion,
                                'QUEUED', :runKey, :now, :now, 0
                            )
                            """)
                    .param("id", proposed.toString())
                    .param("userId", userId.toString())
                    .param("marketDate", marketDate)
                    .param("strategyVersion", properties.strategyVersion())
                    .param("runKey", key)
                    .param("now", clock.instant())
                    .update();
            var runId = jdbc.sql("SELECT BIN_TO_UUID(id) FROM portfolio_analysis_run WHERE run_key=:runKey")
                    .param("runKey", key)
                    .query(UUID.class)
                    .single();
            initialize(runId);
            var payload =
                    "{\"runId\":\"" + runId + "\",\"userId\":\"" + userId + "\",\"marketDate\":\"" + marketDate + "\"}";
            if (jobs.enqueue(
                    "PORTFOLIO_ANALYSIS",
                    "analysis:" + runId + ":PORTFOLIO_ANALYSIS",
                    payload,
                    100,
                    clock.instant(),
                    runId)) scheduled++;
        }
        return scheduled;
    }

    public void started(DurableJobStore.ClaimedJob job) {
        if (job.analysisRunId() == null) return;
        jdbc.sql(
                        """
                        UPDATE portfolio_analysis_step
                        SET status='RUNNING', attempts=attempts+1, updated_at=:now, version=version+1
                        WHERE run_id=UUID_TO_BIN(:runId) AND step_type=:step AND status IN ('QUEUED','RUNNING')
                        """)
                .param("now", clock.instant())
                .param("runId", job.analysisRunId().toString())
                .param("step", job.jobType())
                .update();
    }

    @Transactional
    public void succeeded(DurableJobStore.ClaimedJob job, JobExecutionResult result) {
        if (job.analysisRunId() == null) return;
        jdbc.sql(
                        """
                        UPDATE portfolio_analysis_step
                        SET status='SUCCEEDED', data_as_of=:dataAsOf, error_code=NULL,
                            updated_at=:now, version=version+1
                        WHERE run_id=UUID_TO_BIN(:runId) AND step_type=:step
                        """)
                .param("dataAsOf", result.dataAsOf())
                .param("now", clock.instant())
                .param("runId", job.analysisRunId().toString())
                .param("step", job.jobType())
                .update();
        var next = jdbc.sql(
                        """
                        SELECT s.step_type, BIN_TO_UUID(r.user_id) user_id, r.market_date
                        FROM portfolio_analysis_step s
                        JOIN portfolio_analysis_run r ON r.id=s.run_id
                        WHERE s.run_id=UUID_TO_BIN(:runId) AND s.depends_on=:step AND s.status='PENDING'
                        """)
                .param("runId", job.analysisRunId().toString())
                .param("step", job.jobType())
                .query(NextStep.class)
                .optional();
        if (next.isPresent()) {
            var value = next.orElseThrow();
            jdbc.sql(
                            """
                            UPDATE portfolio_analysis_step SET status='QUEUED', updated_at=:now, version=version+1
                            WHERE run_id=UUID_TO_BIN(:runId) AND step_type=:step AND status='PENDING'
                            """)
                    .param("now", clock.instant())
                    .param("runId", job.analysisRunId().toString())
                    .param("step", value.stepType())
                    .update();
            var payload = "{\"runId\":\"" + job.analysisRunId() + "\",\"userId\":\"" + value.userId()
                    + "\",\"marketDate\":\"" + value.marketDate() + "\"}";
            if (!jobs.enqueue(
                    value.stepType(),
                    "analysis:" + job.analysisRunId() + ":" + value.stepType(),
                    payload,
                    100,
                    clock.instant(),
                    job.analysisRunId())) {
                throw new IllegalStateException("Dependent analysis step could not be enqueued");
            }
        } else {
            jdbc.sql(
                            """
                            UPDATE portfolio_analysis_run
                            SET status='SUCCEEDED', completed_at=:now, data_as_of=:dataAsOf,
                                error_code=NULL, updated_at=:now, version=version+1
                            WHERE id=UUID_TO_BIN(:runId)
                              AND NOT EXISTS (SELECT 1 FROM portfolio_analysis_step s
                                              WHERE s.run_id=portfolio_analysis_run.id AND s.status<>'SUCCEEDED')
                            """)
                    .param("now", clock.instant())
                    .param("dataAsOf", result.dataAsOf())
                    .param("runId", job.analysisRunId().toString())
                    .update();
        }
    }

    public void retrying(DurableJobStore.ClaimedJob job, String code) {
        if (job.analysisRunId() == null) return;
        jdbc.sql(
                        """
                        UPDATE portfolio_analysis_step
                        SET status='QUEUED', error_code=:code, updated_at=:now, version=version+1
                        WHERE run_id=UUID_TO_BIN(:runId) AND step_type=:step
                        """)
                .param("code", code)
                .param("now", clock.instant())
                .param("runId", job.analysisRunId().toString())
                .param("step", job.jobType())
                .update();
    }

    @Transactional
    public void failed(DurableJobStore.ClaimedJob job, String code) {
        if (job.analysisRunId() == null) return;
        jdbc.sql(
                        """
                        UPDATE portfolio_analysis_step
                        SET status=IF(step_type=:step,'FAILED','BLOCKED'), error_code=:code,
                            updated_at=:now, version=version+1
                        WHERE run_id=UUID_TO_BIN(:runId)
                          AND (step_type=:step OR status IN ('PENDING','QUEUED'))
                        """)
                .param("step", job.jobType())
                .param("code", code)
                .param("now", clock.instant())
                .param("runId", job.analysisRunId().toString())
                .update();
        jdbc.sql(
                        """
                        UPDATE portfolio_analysis_run
                        SET status='FAILED', completed_at=:now, error_code=:code,
                            updated_at=:now, version=version+1
                        WHERE id=UUID_TO_BIN(:runId)
                        """)
                .param("now", clock.instant())
                .param("code", code)
                .param("runId", job.analysisRunId().toString())
                .update();
    }

    record NextStep(String stepType, UUID userId, LocalDate marketDate) {}
}
