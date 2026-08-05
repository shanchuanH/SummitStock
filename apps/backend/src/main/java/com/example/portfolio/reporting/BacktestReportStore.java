package com.example.portfolio.reporting;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
class BacktestReportStore {
    private final JdbcClient jdbc;

    BacktestReportStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Optional<RunView> latest(String username) {
        return jdbc.sql(RUN_SELECT + " ORDER BY r.completed_at DESC LIMIT 1")
                .param("username", username)
                .query(RunRow.class)
                .optional()
                .map(this::view);
    }

    Optional<RunView> find(String username, UUID id) {
        return jdbc.sql(RUN_SELECT + " AND r.id=UUID_TO_BIN(:id)")
                .param("username", username)
                .param("id", id.toString())
                .query(RunRow.class)
                .optional()
                .map(this::view);
    }

    @Transactional
    boolean saveCompleted(
            String username,
            UUID id,
            String key,
            String strategyVersion,
            LocalDate start,
            LocalDate end,
            LocalDate trainingThrough,
            LocalDate outOfSampleFrom,
            String universeChecksum,
            String configChecksum,
            String summaryJson,
            List<Metric> metrics,
            Instant completedAt) {
        var inserted = jdbc.sql(
                        """
                INSERT IGNORE INTO backtest_run (id, user_id, idempotency_key, strategy_version, period_start,
                  period_end, training_through, out_of_sample_from, universe_checksum, config_checksum, status,
                  bias_status, summary_json, started_at, completed_at, created_at)
                SELECT UUID_TO_BIN(:id), u.id, :key, :version, :start, :end, :training, :oos, :universe, :config,
                  'SUCCEEDED', 'CLEAR', CAST(:summary AS JSON), :completed, :completed, :completed
                FROM app_user u WHERE u.email=:username
                """)
                .param("id", id.toString())
                .param("key", key)
                .param("version", strategyVersion)
                .param("start", start)
                .param("end", end)
                .param("training", trainingThrough)
                .param("oos", outOfSampleFrom)
                .param("universe", universeChecksum)
                .param("config", configChecksum)
                .param("summary", summaryJson)
                .param("completed", completedAt)
                .param("username", username)
                .update();
        if (inserted == 0) return false;
        for (var metric : metrics) {
            jdbc.sql(
                            """
                    INSERT INTO backtest_metric (id, backtest_run_id, metric_name, metric_value, sample_count,
                      sleeve, horizon_days, created_at)
                    VALUES (UUID_TO_BIN(:id), UUID_TO_BIN(:runId), :name, :value, :samples, :sleeve, :horizon, :at)
                    """)
                    .param("id", UUID.randomUUID().toString())
                    .param("runId", id.toString())
                    .param("name", metric.name())
                    .param("value", metric.value())
                    .param("samples", metric.sampleCount())
                    .param("sleeve", metric.sleeve())
                    .param("horizon", metric.horizonDays())
                    .param("at", completedAt)
                    .update();
        }
        return true;
    }

    private RunView view(RunRow row) {
        var metrics = jdbc.sql(
                        """
                SELECT metric_name name, metric_value value, sample_count sampleCount, sleeve, horizon_days horizonDays
                FROM backtest_metric WHERE backtest_run_id=UUID_TO_BIN(:id) ORDER BY metric_name, horizon_days
                """)
                .param("id", row.id().toString())
                .query(Metric.class)
                .list();
        return new RunView(
                row.id(),
                row.strategyVersion(),
                row.periodStart(),
                row.periodEnd(),
                row.trainingThrough(),
                row.outOfSampleFrom(),
                row.status(),
                row.biasStatus(),
                row.summaryJson(),
                instant(row.completedAt()),
                metrics);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static final String RUN_SELECT =
            """
            SELECT BIN_TO_UUID(r.id) id, r.strategy_version strategyVersion, r.period_start periodStart,
              r.period_end periodEnd, r.training_through trainingThrough, r.out_of_sample_from outOfSampleFrom,
              r.status, r.bias_status biasStatus, CAST(r.summary_json AS CHAR) summaryJson, r.completed_at completedAt
            FROM backtest_run r JOIN app_user u ON u.id=r.user_id
            WHERE u.email=:username AND r.status='SUCCEEDED'
            """;

    record RunRow(
            UUID id,
            String strategyVersion,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate trainingThrough,
            LocalDate outOfSampleFrom,
            String status,
            String biasStatus,
            String summaryJson,
            LocalDateTime completedAt) {}

    record RunView(
            UUID id,
            String strategyVersion,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate trainingThrough,
            LocalDate outOfSampleFrom,
            String status,
            String biasStatus,
            String summaryJson,
            Instant completedAt,
            List<Metric> metrics) {}

    record Metric(String name, double value, long sampleCount, String sleeve, int horizonDays) {}
}
