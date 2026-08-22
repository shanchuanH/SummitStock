package com.example.portfolio.runtime;

import com.example.portfolio.configuration.PortfolioProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
            "COLLECT_EARNINGS_CALENDAR",
            "COMPUTE_VALUATION",
            "COMPUTE_INDICATORS",
            "COMPUTE_PRICE_STATE",
            "COLLECT_MACRO",
            "COMPUTE_REGIME",
            "SYNC_PORTFOLIO",
            "CAPTURE_POSITION_MARKS",
            "COMPUTE_DRAWDOWN_SOURCE",
            "RECALCULATE_STOPS",
            "COMPUTE_EARNINGS_RISK",
            "CHECK_ACTIVE_THESES",
            "UPDATE_DIP_EVENTS",
            "COMPUTE_HOLDING_ANALYSIS",
            "GENERATE_RECOMMENDATIONS",
            "COUNT_ACTIVE_RECOMMENDATIONS");

    static final Map<String, List<String>> DEPENDENCIES = dependencies();

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
        for (var step : PIPELINE) {
            jdbc.sql(
                            """
                            INSERT IGNORE INTO portfolio_analysis_step (
                                id, run_id, step_type, status, depends_on, attempts, created_at, updated_at, version
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:runId), :step, :status, NULL, 0, :now, :now, 0
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("runId", runId.toString())
                    .param("step", step)
                    .param("status", "PORTFOLIO_ANALYSIS".equals(step) ? "QUEUED" : "PENDING")
                    .param("now", clock.instant())
                    .update();
            for (var dependency : DEPENDENCIES.getOrDefault(step, List.of())) {
                jdbc.sql(
                                """
                                INSERT IGNORE INTO portfolio_analysis_step_dependency (run_id,step_type,depends_on)
                                VALUES (UUID_TO_BIN(:runId),:step,:dependency)
                                """)
                        .param("runId", runId.toString())
                        .param("step", step)
                        .param("dependency", dependency)
                        .update();
            }
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
            if (!createAndSchedule(userId, marketDate, properties.strategyVersion(), null, key)
                    .alreadyRunning()) {
                scheduled++;
            }
        }
        return scheduled;
    }

    @Transactional
    public Optional<ScheduleResult> scheduleForUser(
            String email, LocalDate marketDate, PortfolioProperties properties) {
        var userId = jdbc.sql(
                        """
                        SELECT DISTINCT BIN_TO_UUID(u.id)
                        FROM app_user u JOIN investment_account a ON a.user_id=u.id
                        WHERE u.email=:email AND u.status='ACTIVE' AND a.active=TRUE
                        """)
                .param("email", email)
                .query(UUID.class)
                .optional();
        if (userId.isEmpty()) return Optional.empty();
        var activeRun = jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(r.id) id,
                               GREATEST(r.updated_at,COALESCE((
                                 SELECT MAX(s.updated_at) FROM portfolio_analysis_step s WHERE s.run_id=r.id
                               ),r.updated_at)) updatedAt
                        FROM portfolio_analysis_run r
                        WHERE r.user_id=UUID_TO_BIN(:userId) AND r.status IN ('QUEUED','RUNNING','WAITING')
                        ORDER BY r.created_at DESC LIMIT 1
                        """)
                .param("userId", userId.orElseThrow().toString())
                .query(ActiveRun.class)
                .optional();
        if (activeRun.isPresent()) {
            var active = activeRun.orElseThrow();
            var stale =
                    java.time.Duration.between(active.updatedAt().toInstant(java.time.ZoneOffset.UTC), clock.instant())
                                    .compareTo(java.time.Duration.ofSeconds(120))
                            >= 0;
            if (!stale) return Optional.of(new ScheduleResult(active.id(), true));
            abandonStalled(active.id());
        }
        var runKey = "manual:" + userId.orElseThrow() + ":" + marketDate + ":" + properties.strategyVersion() + ":"
                + UUID.randomUUID();
        return Optional.of(
                createAndSchedule(userId.orElseThrow(), marketDate, properties.strategyVersion(), null, runKey));
    }

    @Transactional
    public ScheduleResult createAndSchedule(
            UUID userId, LocalDate marketDate, String strategyVersion, UUID importBatchId, String runKey) {
        var proposed = UUID.randomUUID();
        var created = jdbc.sql(
                        """
                        INSERT IGNORE INTO portfolio_analysis_run (
                            id,user_id,import_batch_id,market_date,strategy_version,status,run_key,created_at,updated_at,version
                        ) VALUES (
                            UUID_TO_BIN(:id),UUID_TO_BIN(:userId),UUID_TO_BIN(:importBatchId),:marketDate,:strategyVersion,
                            'QUEUED',:runKey,:now,:now,0
                        )
                        """)
                .param("id", proposed.toString())
                .param("userId", userId.toString())
                .param("marketDate", marketDate)
                .param("strategyVersion", strategyVersion)
                .param("importBatchId", importBatchId == null ? null : importBatchId.toString(), java.sql.Types.VARCHAR)
                .param("runKey", runKey)
                .param("now", clock.instant())
                .update();
        var runId = jdbc.sql("SELECT BIN_TO_UUID(id) FROM portfolio_analysis_run WHERE run_key=:runKey")
                .param("runKey", runKey)
                .query(UUID.class)
                .single();
        initialize(runId);
        var payload =
                "{\"runId\":\"" + runId + "\",\"userId\":\"" + userId + "\",\"marketDate\":\"" + marketDate + "\"}";
        var rootQueued = jobs.enqueue(
                "PORTFOLIO_ANALYSIS",
                "analysis:" + runId + ":PORTFOLIO_ANALYSIS",
                payload,
                100,
                clock.instant(),
                runId);
        if (created == 1 && !rootQueued) {
            throw new IllegalStateException("Analysis root job could not be enqueued");
        }
        return new ScheduleResult(runId, created == 0);
    }

    private void abandonStalled(UUID runId) {
        jdbc.sql(
                        """
                        UPDATE job_run SET status='DEAD',last_error_code='STALLED_ABANDONED',updated_at=:now,version=version+1,
                          lease_owner=NULL,lease_token=NULL,lease_expires_at=NULL
                        WHERE analysis_run_id=UUID_TO_BIN(:runId) AND status IN ('PENDING','RUNNING')
                        """)
                .param("now", clock.instant())
                .param("runId", runId.toString())
                .update();
        jdbc.sql(
                        """
                        UPDATE portfolio_analysis_step SET status=IF(status='RUNNING','FAILED','BLOCKED'),
                          error_code='STALLED_ABANDONED',updated_at=:now,version=version+1
                        WHERE run_id=UUID_TO_BIN(:runId) AND status IN ('PENDING','QUEUED','RUNNING')
                        """)
                .param("now", clock.instant())
                .param("runId", runId.toString())
                .update();
        jdbc.sql(
                        """
                        UPDATE portfolio_analysis_run SET status='FAILED',completed_at=:now,error_code='STALLED_ABANDONED',
                          updated_at=:now,version=version+1 WHERE id=UUID_TO_BIN(:runId)
                        """)
                .param("now", clock.instant())
                .param("runId", runId.toString())
                .update();
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
                        SET status='SUCCEEDED', data_as_of=:dataAsOf, error_code=:evidenceStatus,
                            updated_at=:now, version=version+1
                        WHERE run_id=UUID_TO_BIN(:runId) AND step_type=:step
                        """)
                .param("dataAsOf", result.dataAsOf())
                .param("evidenceStatus", "PARTIAL".equals(result.status()) ? "PARTIAL_EVIDENCE" : null)
                .param("now", clock.instant())
                .param("runId", job.analysisRunId().toString())
                .param("step", job.jobType())
                .update();
        var ready = jdbc.sql(
                        """
                        SELECT s.step_type, BIN_TO_UUID(r.user_id) user_id, r.market_date
                        FROM portfolio_analysis_step s
                        JOIN portfolio_analysis_run r ON r.id=s.run_id
                        WHERE s.run_id=UUID_TO_BIN(:runId) AND s.status='PENDING'
                          AND EXISTS (SELECT 1 FROM portfolio_analysis_step_dependency d
                                      WHERE d.run_id=s.run_id AND d.step_type=s.step_type)
                          AND NOT EXISTS (
                              SELECT 1 FROM portfolio_analysis_step_dependency d
                              JOIN portfolio_analysis_step prerequisite
                                ON prerequisite.run_id=d.run_id AND prerequisite.step_type=d.depends_on
                              WHERE d.run_id=s.run_id AND d.step_type=s.step_type
                                AND prerequisite.status<>'SUCCEEDED'
                          )
                        ORDER BY s.created_at,s.step_type
                        """)
                .param("runId", job.analysisRunId().toString())
                .query(NextStep.class)
                .list();
        for (var value : ready) {
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
            jobs.enqueue(
                    value.stepType(),
                    "analysis:" + job.analysisRunId() + ":" + value.stepType(),
                    payload,
                    100,
                    clock.instant(),
                    job.analysisRunId());
        }
        jdbc.sql(
                        """
                            UPDATE portfolio_analysis_run
                            SET status=IF(EXISTS (
                                    SELECT 1 FROM portfolio_analysis_step partial
                                    WHERE partial.run_id=portfolio_analysis_run.id
                                      AND partial.error_code='PARTIAL_EVIDENCE'
                                ),'PARTIAL','SUCCEEDED'),
                                completed_at=:now, data_as_of=:dataAsOf,
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

    record ActiveRun(UUID id, java.time.LocalDateTime updatedAt) {}

    public record ScheduleResult(UUID runId, boolean alreadyRunning) {}

    public static int canonicalDependencyCount() {
        return DEPENDENCIES.values().stream().mapToInt(List::size).sum();
    }

    private static Map<String, List<String>> dependencies() {
        var values = new LinkedHashMap<String, List<String>>();
        values.put("COLLECT_QUOTES", List.of("PORTFOLIO_ANALYSIS"));
        values.put("COLLECT_BARS", List.of("PORTFOLIO_ANALYSIS"));
        values.put("VALIDATE_BARS", List.of("COLLECT_BARS"));
        values.put("COLLECT_CORPORATE_ACTIONS", List.of("COLLECT_BARS"));
        values.put("CHECK_FILINGS", List.of("PORTFOLIO_ANALYSIS"));
        values.put("COLLECT_FUNDAMENTALS", List.of("CHECK_FILINGS"));
        values.put("NORMALIZE_FINANCIALS", List.of("COLLECT_FUNDAMENTALS"));
        values.put("COMPUTE_FINANCIAL_HEALTH", List.of("NORMALIZE_FINANCIALS"));
        values.put("COLLECT_ESTIMATES", List.of("PORTFOLIO_ANALYSIS"));
        values.put("COMPUTE_REVISIONS", List.of("COLLECT_ESTIMATES"));
        values.put("COLLECT_EARNINGS_CALENDAR", List.of("PORTFOLIO_ANALYSIS"));
        values.put("COMPUTE_INDICATORS", List.of("VALIDATE_BARS"));
        values.put("COMPUTE_PRICE_STATE", List.of("COMPUTE_INDICATORS"));
        values.put("COLLECT_MACRO", List.of("PORTFOLIO_ANALYSIS"));
        values.put("COMPUTE_REGIME", List.of("COLLECT_MACRO", "COMPUTE_PRICE_STATE"));
        values.put("SYNC_PORTFOLIO", List.of("COLLECT_QUOTES", "COLLECT_CORPORATE_ACTIONS"));
        values.put("CAPTURE_POSITION_MARKS", List.of("SYNC_PORTFOLIO"));
        values.put("COMPUTE_VALUATION", List.of("COMPUTE_FINANCIAL_HEALTH", "COMPUTE_REVISIONS", "COLLECT_QUOTES"));
        values.put("COMPUTE_DRAWDOWN_SOURCE", List.of("CAPTURE_POSITION_MARKS"));
        values.put("RECALCULATE_STOPS", List.of("CAPTURE_POSITION_MARKS", "COMPUTE_PRICE_STATE"));
        values.put("COMPUTE_EARNINGS_RISK", List.of("COLLECT_EARNINGS_CALENDAR", "COMPUTE_PRICE_STATE"));
        values.put("CHECK_ACTIVE_THESES", List.of("SYNC_PORTFOLIO"));
        values.put("UPDATE_DIP_EVENTS", List.of("COMPUTE_REGIME", "CAPTURE_POSITION_MARKS"));
        values.put(
                "COMPUTE_HOLDING_ANALYSIS",
                List.of(
                        "COMPUTE_FINANCIAL_HEALTH",
                        "COMPUTE_VALUATION",
                        "COMPUTE_REVISIONS",
                        "COMPUTE_PRICE_STATE",
                        "COMPUTE_REGIME",
                        "COMPUTE_DRAWDOWN_SOURCE",
                        "RECALCULATE_STOPS",
                        "COMPUTE_EARNINGS_RISK",
                        "CHECK_ACTIVE_THESES",
                        "UPDATE_DIP_EVENTS"));
        values.put("GENERATE_RECOMMENDATIONS", List.of("COMPUTE_HOLDING_ANALYSIS"));
        values.put("COUNT_ACTIVE_RECOMMENDATIONS", List.of("GENERATE_RECOMMENDATIONS"));
        return java.util.Collections.unmodifiableMap(values);
    }
}
