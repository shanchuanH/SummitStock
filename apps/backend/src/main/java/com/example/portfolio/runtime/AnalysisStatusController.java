package com.example.portfolio.runtime;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/analysis")
public class AnalysisStatusController {
    private static final Map<String, StageDefinition> STAGES = stages();
    private final JdbcClient jdbc;
    private final WorkerHeartbeatStore heartbeats;
    private final Clock clock;

    public AnalysisStatusController(JdbcClient jdbc, WorkerHeartbeatStore heartbeats, Clock clock) {
        this.jdbc = jdbc;
        this.heartbeats = heartbeats;
        this.clock = clock;
    }

    @GetMapping("/status/{runId}")
    AnalysisStatusResponse status(@PathVariable UUID runId, Principal principal) {
        var run = jdbc.sql(
                        """
                        SELECT r.status,r.started_at startedAt,r.updated_at updatedAt,r.error_code errorCode
                        FROM portfolio_analysis_run r JOIN app_user u ON u.id=r.user_id
                        WHERE r.id=UUID_TO_BIN(:runId) AND u.email=:email
                        """)
                .param("runId", runId.toString())
                .param("email", principal.getName())
                .query(RunRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis run not found"));
        var steps = jdbc.sql(
                        """
                        SELECT step_type stepType,status,error_code errorCode,updated_at updatedAt
                        FROM portfolio_analysis_step WHERE run_id=UUID_TO_BIN(:runId)
                        """)
                .param("runId", runId.toString())
                .query(StepRow.class)
                .list();
        var byType = new java.util.HashMap<String, StepRow>();
        steps.forEach(step -> byType.put(step.stepType(), step));
        var grouped = STAGES.entrySet().stream()
                .map(entry -> new ProgressStage(
                        entry.getKey(),
                        entry.getValue().label(),
                        stageStatus(entry.getValue().stepTypes(), byType)))
                .toList();
        var runtime = heartbeats.snapshot();
        var completed =
                steps.stream().filter(step -> "SUCCEEDED".equals(step.status())).count();
        var current = steps.stream()
                .filter(step -> Set.of("RUNNING", "QUEUED").contains(step.status()))
                .sorted(java.util.Comparator.comparingInt(
                        step -> AnalysisRunOrchestrator.PIPELINE.indexOf(step.stepType())))
                .map(StepRow::stepType)
                .findFirst()
                .orElse(null);
        var failed = steps.stream()
                .filter(step -> "FAILED".equals(step.status()))
                .findFirst()
                .orElse(null);
        var lastProgressAt = steps.stream()
                .map(StepRow::updatedAt)
                .filter(java.util.Objects::nonNull)
                .max(java.util.Comparator.naturalOrder())
                .orElse(run.updatedAt());
        var pendingAge = "QUEUED".equals(run.status()) || "RUNNING".equals(run.status())
                ? Math.max(
                        0,
                        Duration.between(lastProgressAt.toInstant(ZoneOffset.UTC), clock.instant())
                                .toSeconds())
                : null;
        var state = externalState(run.status(), runtime.workerAlive(), pendingAge);
        var errorCode = failed == null ? run.errorCode() : failed.errorCode();
        return new AnalysisStatusResponse(
                runId,
                state,
                new WorkerStatus(runtime.workerAlive(), runtime.lastHeartbeat()),
                new ProgressStatus(
                        completed,
                        AnalysisRunOrchestrator.PIPELINE.size(),
                        current,
                        lastProgressAt.toInstant(ZoneOffset.UTC)),
                failed == null
                        ? null
                        : new FailureStatus(
                                failed.stepType(), errorCode, failureMessage(errorCode), retryable(errorCode)),
                pendingAge,
                run.startedAt() == null ? null : run.startedAt().toInstant(ZoneOffset.UTC),
                category(current),
                grouped);
    }

    private static String stageStatus(Set<String> stepTypes, Map<String, StepRow> steps) {
        var values = stepTypes.stream()
                .map(steps::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (values.stream().anyMatch(value -> Set.of("FAILED", "BLOCKED").contains(value.status()))) return "FAILED";
        if (!values.isEmpty() && values.stream().allMatch(value -> "SUCCEEDED".equals(value.status()))) {
            return "COMPLETE";
        }
        if (values.stream().anyMatch(value -> Set.of("RUNNING", "QUEUED").contains(value.status()))) return "RUNNING";
        return "STARTING";
    }

    static String externalState(String status, boolean workerAlive, Long pendingAge) {
        if (Set.of("QUEUED", "RUNNING").contains(status) && !workerAlive) return "WORKER_OFFLINE";
        if (Set.of("QUEUED", "RUNNING").contains(status) && pendingAge != null && pendingAge >= 120) return "STALLED";
        return switch (status) {
            case "QUEUED" -> "STARTING";
            case "RUNNING" -> "RUNNING";
            case "WAITING" -> "PARTIAL";
            case "PARTIAL" -> "PARTIAL";
            case "SUCCEEDED" -> "READY";
            case "BLOCKED" -> "BLOCKED";
            case "FAILED" -> "FAILED";
            default -> "FAILED";
        };
    }

    private static String failureMessage(String code) {
        if (code == null) return "Analysis stopped before completion.";
        return switch (code) {
            case "FORMAL_RECOMMENDATION_STRATEGY_REJECTED" ->
                "The strategy publication gate blocked formal recommendations.";
            case "MISSING_ANALYSIS_RUN" -> "The analysis run context was missing.";
            default -> "Analysis stopped at a durable pipeline stage (" + code + ").";
        };
    }

    private static boolean retryable(String code) {
        return code != null
                && (code.contains("TIMEOUT") || code.contains("RATE_LIMIT") || code.contains("UNAVAILABLE"));
    }

    private static String category(String stage) {
        if (stage == null) return null;
        if (stage.startsWith("COLLECT") || stage.startsWith("CHECK_FILINGS")) return "DATA_COLLECTION";
        if (stage.contains("RISK") || stage.contains("DRAWDOWN") || stage.contains("STOPS")) return "RISK";
        if (stage.contains("RECOMMENDATION") || stage.contains("HOLDING_ANALYSIS")) return "DECISION";
        return "COMPUTATION";
    }

    private static Map<String, StageDefinition> stages() {
        var values = new LinkedHashMap<String, StageDefinition>();
        values.put("HOLDINGS", new StageDefinition("持仓", Set.of("PORTFOLIO_ANALYSIS", "SYNC_PORTFOLIO")));
        values.put(
                "PRICES", new StageDefinition("市场价格", Set.of("COLLECT_QUOTES", "COLLECT_BARS", "COMPUTE_PRICE_STATE")));
        values.put(
                "FINANCIALS",
                new StageDefinition(
                        "公司财务", Set.of("COLLECT_FUNDAMENTALS", "NORMALIZE_FINANCIALS", "COMPUTE_FINANCIAL_HEALTH")));
        values.put(
                "ESTIMATES",
                new StageDefinition(
                        "盈利预测", Set.of("COLLECT_ESTIMATES", "COMPUTE_REVISIONS", "COLLECT_EARNINGS_CALENDAR")));
        values.put("MARKET", new StageDefinition("市场环境", Set.of("COLLECT_MACRO", "COMPUTE_REGIME")));
        values.put(
                "PORTFOLIO_RISK",
                new StageDefinition(
                        "风险计算", Set.of("CAPTURE_POSITION_MARKS", "COMPUTE_DRAWDOWN_SOURCE", "RECALCULATE_STOPS")));
        values.put("HOLDING_ANALYSIS", new StageDefinition("个股结论", Set.of("COMPUTE_HOLDING_ANALYSIS")));
        values.put(
                "TODAY_BRIEF",
                new StageDefinition("今日简报", Set.of("GENERATE_RECOMMENDATIONS", "COUNT_ACTIVE_RECOMMENDATIONS")));
        return java.util.Collections.unmodifiableMap(values);
    }

    record StageDefinition(String label, Set<String> stepTypes) {}

    record RunRow(String status, LocalDateTime startedAt, LocalDateTime updatedAt, String errorCode) {}

    record StepRow(String stepType, String status, String errorCode, LocalDateTime updatedAt) {}

    public record ProgressStage(String code, String label, String status) {}

    public record WorkerStatus(boolean alive, Instant lastSeenAt) {}

    public record ProgressStatus(long completed, int total, String currentStage, Instant lastProgressAt) {}

    public record FailureStatus(String failedStage, String errorCode, String errorMessage, boolean retryable) {}

    public record AnalysisStatusResponse(
            UUID runId,
            String state,
            WorkerStatus worker,
            ProgressStatus progress,
            FailureStatus failure,
            Long pendingAgeSeconds,
            Instant startedAt,
            String estimatedCategory,
            List<ProgressStage> stages) {}
}
