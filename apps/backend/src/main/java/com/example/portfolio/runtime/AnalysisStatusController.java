package com.example.portfolio.runtime;

import java.security.Principal;
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

    public AnalysisStatusController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/status/{runId}")
    AnalysisStatusResponse status(@PathVariable UUID runId, Principal principal) {
        var run = jdbc.sql(
                        """
                        SELECT r.status,r.updated_at updatedAt FROM portfolio_analysis_run r
                        JOIN app_user u ON u.id=r.user_id
                        WHERE r.id=UUID_TO_BIN(:runId) AND u.email=:email
                        """)
                .param("runId", runId.toString())
                .param("email", principal.getName())
                .query(RunRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Analysis run not found"));
        var jobs = jdbc.sql(
                        """
                        SELECT job_type jobType,status FROM job_run
                        WHERE analysis_run_id=UUID_TO_BIN(:runId)
                        """)
                .param("runId", runId.toString())
                .query(JobRow.class)
                .list();
        var byType = new java.util.HashMap<String, String>();
        jobs.forEach(job -> byType.put(job.jobType(), job.status()));
        var progress = STAGES.entrySet().stream()
                .map(entry -> new ProgressStage(
                        entry.getKey(),
                        entry.getValue().label(),
                        stageStatus(entry.getValue().jobTypes(), byType, run.status())))
                .toList();
        return new AnalysisStatusResponse(
                runId,
                externalState(run.status()),
                progress,
                progress.stream()
                        .filter(stage -> "COMPLETE".equals(stage.status()))
                        .count(),
                progress.size(),
                run.updatedAt().toInstant(ZoneOffset.UTC));
    }

    private static String stageStatus(Set<String> jobTypes, Map<String, String> jobs, String runStatus) {
        var values = jobTypes.stream()
                .map(jobs::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        if (values.stream().anyMatch(value -> Set.of("FAILED", "PERMANENT_FAILURE", "BLOCKED")
                .contains(value))) {
            return "FAILED";
        }
        if (!values.isEmpty() && values.stream().allMatch("SUCCEEDED"::equals)) return "COMPLETE";
        if (values.stream().anyMatch(value -> Set.of("RUNNING", "CLAIMED").contains(value))) return "RUNNING";
        if ("SUCCEEDED".equals(runStatus) && !values.isEmpty()) return "COMPLETE";
        return "WAITING";
    }

    private static String externalState(String status) {
        return switch (status) {
            case "QUEUED", "RUNNING" -> "ANALYSIS_RUNNING";
            case "WAITING" -> "WAIT_FOR_MARKET_DATA";
            case "PARTIAL" -> "PARTIAL_ANALYSIS";
            case "SUCCEEDED" -> "ANALYSIS_READY";
            default -> status;
        };
    }

    private static Map<String, StageDefinition> stages() {
        var values = new LinkedHashMap<String, StageDefinition>();
        values.put("HOLDINGS", new StageDefinition("Holdings imported", Set.of("PORTFOLIO_ANALYSIS")));
        values.put(
                "PRICES",
                new StageDefinition("Prices", Set.of("COLLECT_QUOTES", "COLLECT_BARS", "COMPUTE_INDICATORS")));
        values.put(
                "FINANCIALS",
                new StageDefinition(
                        "Financial data",
                        Set.of("COLLECT_FUNDAMENTALS", "NORMALIZE_FINANCIALS", "COMPUTE_FINANCIAL_HEALTH")));
        values.put("VALUATION", new StageDefinition("Valuation", Set.of("COMPUTE_VALUATION")));
        values.put(
                "EVENTS",
                new StageDefinition(
                        "Analyst estimates and earnings",
                        Set.of("COLLECT_ESTIMATES", "COMPUTE_REVISIONS", "COLLECT_EARNINGS_CALENDAR")));
        values.put(
                "PORTFOLIO_RISK",
                new StageDefinition(
                        "Portfolio risk",
                        Set.of("CAPTURE_POSITION_MARKS", "COMPUTE_DRAWDOWN_SOURCE", "RECALCULATE_STOPS")));
        values.put("HOLDING_ANALYSIS", new StageDefinition("Holding analysis", Set.of("COMPUTE_HOLDING_ANALYSIS")));
        values.put(
                "TODAY_BRIEF",
                new StageDefinition(
                        "Today's brief", Set.of("GENERATE_RECOMMENDATIONS", "COUNT_ACTIVE_RECOMMENDATIONS")));
        return java.util.Collections.unmodifiableMap(values);
    }

    record StageDefinition(String label, Set<String> jobTypes) {}

    record RunRow(String status, LocalDateTime updatedAt) {}

    record JobRow(String jobType, String status) {}

    public record ProgressStage(String code, String label, String status) {}

    public record AnalysisStatusResponse(
            UUID runId,
            String state,
            List<ProgressStage> stages,
            long completedStages,
            int totalStages,
            Instant updatedAt) {}
}
