package com.example.portfolio.portfolioimport.application;

import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.portfolioimport.infrastructure.PortfolioImportStore;
import com.example.portfolio.runtime.DurableJobStore;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PortfolioImportConfirmationService {
    private final PortfolioImportStore imports;
    private final PortfolioReconciliationService reconciliation;
    private final DurableJobStore jobs;
    private final PortfolioProperties properties;
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public PortfolioImportConfirmationService(
            PortfolioImportStore imports,
            PortfolioReconciliationService reconciliation,
            DurableJobStore jobs,
            PortfolioProperties properties,
            JdbcClient jdbc,
            ObjectMapper json,
            Clock clock) {
        this.imports = imports;
        this.reconciliation = reconciliation;
        this.jobs = jobs;
        this.properties = properties;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public ConfirmationResult confirm(String email, UUID batchId, ConfirmCommand command) {
        var batch = imports.batch(email, batchId);
        if ("CONFIRMED".equals(batch.status())) return result(batchId, true, 0);
        if (!"PREVIEW".equals(batch.status())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Import batch cannot be confirmed from " + batch.status());
        }
        if (batch.version() != command.expectedVersion()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Import batch version changed");
        }
        int claimed = jdbc.sql(
                        """
                        UPDATE portfolio_import_batch b
                        JOIN app_user u ON u.id=b.user_id
                        SET b.status='IMPORTING', b.updated_at=:now, b.version=b.version+1
                        WHERE b.id=UUID_TO_BIN(:batchId) AND u.email=:email
                          AND b.status='PREVIEW' AND b.version=:version
                        """)
                .param("now", clock.instant())
                .param("batchId", batchId.toString())
                .param("email", email)
                .param("version", command.expectedVersion())
                .update();
        if (claimed != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Import batch version changed");

        var reconciled = reconciliation.reconcile(email, batch.userId(), batchId, imports.rows(batchId), command);
        refreshCounts(batchId);
        var runId = createAnalysisRun(batch.userId(), batchId);
        boolean enqueued = jobs.enqueue(
                "PORTFOLIO_ANALYSIS",
                "portfolio-analysis:" + runId,
                payload(email, batch.userId(), runId, batchId),
                100,
                clock.instant(),
                runId);
        if (!enqueued) throw new IllegalStateException("Portfolio analysis job could not be enqueued");
        jdbc.sql(
                        """
                        UPDATE portfolio_import_batch
                        SET status='CONFIRMED', confirmed_at=:now, updated_at=:now, version=version+1
                        WHERE id=UUID_TO_BIN(:batchId) AND status='IMPORTING'
                        """)
                .param("now", clock.instant())
                .param("batchId", batchId.toString())
                .update();
        audit(batch.userId(), batchId, runId, reconciled);
        var confirmed = imports.batch(email, batchId);
        return new ConfirmationResult(
                batchId,
                confirmed.status(),
                confirmed.version(),
                runId,
                "ANALYSIS_QUEUED",
                reconciled.openPositionCount(),
                reconciled.closedPositionCount(),
                reconciled.cashRowCount(),
                reconciled.compensationRowCount(),
                false);
    }

    private UUID createAnalysisRun(UUID userId, UUID batchId) {
        var runId = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO portfolio_analysis_run (
                            id, user_id, import_batch_id, market_date, strategy_version, status, run_key,
                            created_at, updated_at, version
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), UUID_TO_BIN(:batchId), :marketDate,
                            :strategyVersion, 'QUEUED', :runKey, :now, :now, 0
                        ) ON DUPLICATE KEY UPDATE id=id
                        """)
                .param("id", runId.toString())
                .param("userId", userId.toString())
                .param("batchId", batchId.toString())
                .param("marketDate", LocalDate.now(clock))
                .param("strategyVersion", properties.strategyVersion())
                .param("runKey", "import:" + batchId)
                .param("now", clock.instant())
                .update();
        var persisted = jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) FROM portfolio_analysis_run
                        WHERE user_id=UUID_TO_BIN(:userId) AND import_batch_id=UUID_TO_BIN(:batchId)
                        """)
                .param("userId", userId.toString())
                .param("batchId", batchId.toString())
                .query(UUID.class)
                .single();
        jdbc.sql(
                        """
                        INSERT IGNORE INTO portfolio_analysis_step (
                            id, run_id, step_type, status, attempts, created_at, updated_at, version
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:runId), 'PORTFOLIO_ANALYSIS', 'QUEUED', 0, :now, :now, 0
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("runId", persisted.toString())
                .param("now", clock.instant())
                .update();
        return persisted;
    }

    private ConfirmationResult result(UUID batchId, boolean replay, int closedCount) {
        var value = jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(b.id) batch_id, b.status, b.version,
                               BIN_TO_UUID(r.id) analysis_run_id, r.status analysis_status,
                               (SELECT COUNT(DISTINCT s.position_id) FROM position_snapshot s
                                WHERE s.import_batch_id=b.id) open_position_count,
                               (SELECT COUNT(*) FROM portfolio_import_row ir
                                WHERE ir.batch_id=b.id AND ir.row_type='CASH' AND ir.status<>'IGNORED') cash_row_count,
                               (SELECT COUNT(*) FROM compensation_holding c
                                WHERE c.import_batch_id=b.id) compensation_row_count
                        FROM portfolio_import_batch b
                        LEFT JOIN portfolio_analysis_run r ON r.import_batch_id=b.id AND r.user_id=b.user_id
                        WHERE b.id=UUID_TO_BIN(:batchId)
                        """)
                .param("batchId", batchId.toString())
                .query(ReplayResult.class)
                .single();
        return new ConfirmationResult(
                value.batchId(),
                value.status(),
                value.version(),
                value.analysisRunId(),
                analysisState(value.analysisStatus()),
                value.openPositionCount(),
                closedCount,
                value.cashRowCount(),
                value.compensationRowCount(),
                replay);
    }

    private static String analysisState(String status) {
        return switch (status) {
            case "QUEUED", "RUNNING" -> "ANALYSIS_QUEUED";
            case "WAITING" -> "WAIT_FOR_MARKET_DATA";
            case "PARTIAL" -> "PARTIAL_ANALYSIS";
            case "SUCCEEDED" -> "ANALYSIS_READY";
            case "BLOCKED" -> "BLOCKED";
            case "FAILED" -> "FAILED";
            default -> "PORTFOLIO_READY";
        };
    }

    private void refreshCounts(UUID batchId) {
        jdbc.sql(
                        """
                        UPDATE portfolio_import_batch b SET
                            valid_row_count=(SELECT COUNT(*) FROM portfolio_import_row r
                                             WHERE r.batch_id=b.id AND r.status IN ('VALID','WARNING','IGNORED')),
                            error_row_count=(SELECT COUNT(*) FROM portfolio_import_row r
                                             WHERE r.batch_id=b.id AND r.status='ERROR')
                        WHERE b.id=UUID_TO_BIN(:batchId)
                        """)
                .param("batchId", batchId.toString())
                .update();
    }

    private void audit(
            UUID userId, UUID batchId, UUID runId, PortfolioReconciliationService.ReconciliationResult result) {
        jdbc.sql(
                        """
                        INSERT INTO audit_log (
                            id, user_id, event_type, entity_type, entity_id, strategy_version,
                            rule_ids, details, occurred_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), 'PORTFOLIO_IMPORT_CONFIRMED',
                            'PORTFOLIO_IMPORT_BATCH', :batchId, :strategyVersion, JSON_ARRAY('IMPORT.CONFIRM.001'),
                            JSON_OBJECT('analysisRunId', :runId, 'openPositions', :openPositions,
                                        'closedPositions', :closedPositions), :now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("batchId", batchId.toString())
                .param("strategyVersion", properties.strategyVersion())
                .param("runId", runId.toString())
                .param("openPositions", result.openPositionCount())
                .param("closedPositions", result.closedPositionCount())
                .param("now", clock.instant())
                .update();
    }

    private String payload(String email, UUID userId, UUID runId, UUID batchId) {
        try {
            return json.writeValueAsString(Map.of(
                    "userEmail", email,
                    "userId", userId.toString(),
                    "runId", runId.toString(),
                    "marketDate", LocalDate.now(clock).toString(),
                    "importBatchId", batchId.toString()));
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to enqueue portfolio analysis", exception);
        }
    }

    public record ConfirmCommand(
            long expectedVersion, List<AccountMapping> accountMappings, List<RowOverride> rowOverrides) {
        public ConfirmCommand {
            accountMappings = accountMappings == null ? List.of() : List.copyOf(accountMappings);
            rowOverrides = rowOverrides == null ? List.of() : List.copyOf(rowOverrides);
        }
    }

    public record AccountMapping(String accountNumberMasked, UUID existingAccountId, String displayName) {}

    public record RowOverride(int rowNumber, String symbol, String assetType, String rowType, boolean ignored) {}

    public record ConfirmationResult(
            UUID batchId,
            String status,
            long version,
            UUID analysisRunId,
            String analysisState,
            int openPositionCount,
            int closedPositionCount,
            int cashRowCount,
            int compensationRowCount,
            boolean idempotentReplay) {}

    record ReplayResult(
            UUID batchId,
            String status,
            long version,
            UUID analysisRunId,
            String analysisStatus,
            int openPositionCount,
            int cashRowCount,
            int compensationRowCount) {}
}
