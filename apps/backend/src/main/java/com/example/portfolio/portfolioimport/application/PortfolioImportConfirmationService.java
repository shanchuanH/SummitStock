package com.example.portfolio.portfolioimport.application;

import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.portfolioimport.infrastructure.PortfolioImportStore;
import com.example.portfolio.runtime.AnalysisRunOrchestrator;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioImportConfirmationService {
    private final PortfolioImportStore imports;
    private final PortfolioReconciliationService reconciliation;
    private final AnalysisRunOrchestrator analysisRuns;
    private final PortfolioProperties properties;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final PortfolioCashflowReconciliationService cashflowReconciliation;

    public PortfolioImportConfirmationService(
            PortfolioImportStore imports,
            PortfolioReconciliationService reconciliation,
            AnalysisRunOrchestrator analysisRuns,
            PortfolioProperties properties,
            JdbcClient jdbc,
            Clock clock,
            PortfolioCashflowReconciliationService cashflowReconciliation) {
        this.imports = imports;
        this.reconciliation = reconciliation;
        this.analysisRuns = analysisRuns;
        this.properties = properties;
        this.jdbc = jdbc;
        this.clock = clock;
        this.cashflowReconciliation = cashflowReconciliation;
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

        var priorCash = cashflowReconciliation.before(batch.userId());
        var reconciled = reconciliation.reconcile(email, batch.userId(), batchId, imports.rows(batchId), command);
        var cashflow = cashflowReconciliation.reconcile(batch.userId(), batchId, priorCash);
        refreshCounts(batchId);
        var waitingForCashflow = "REQUIRED".equals(cashflow.status());
        UUID runId = waitingForCashflow
                ? null
                : analysisRuns
                        .createAndSchedule(
                                batch.userId(),
                                LocalDate.now(clock),
                                properties.strategyVersion(),
                                batchId,
                                "import:" + batchId)
                        .runId();
        jdbc.sql(
                        """
                        UPDATE portfolio_import_batch
                        SET status=:status, confirmed_at=:now, updated_at=:now, version=version+1
                        WHERE id=UUID_TO_BIN(:batchId) AND status='IMPORTING'
                        """)
                .param("now", clock.instant())
                .param("status", waitingForCashflow ? "WAITING_FOR_CASHFLOW_CONFIRMATION" : "CONFIRMED")
                .param("batchId", batchId.toString())
                .update();
        audit(batch.userId(), batchId, runId, reconciled);
        var confirmed = imports.batch(email, batchId);
        return new ConfirmationResult(
                batchId,
                confirmed.status(),
                confirmed.version(),
                runId,
                waitingForCashflow ? "WAITING_FOR_CASHFLOW_CONFIRMATION" : "ANALYSIS_QUEUED",
                reconciled.openPositionCount(),
                reconciled.closedPositionCount(),
                reconciled.cashRowCount(),
                reconciled.compensationRowCount(),
                false,
                cashflow);
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
                "WAITING_FOR_CASHFLOW_CONFIRMATION".equals(value.status())
                        ? "WAITING_FOR_CASHFLOW_CONFIRMATION"
                        : analysisState(value.analysisStatus()),
                value.openPositionCount(),
                closedCount,
                value.cashRowCount(),
                value.compensationRowCount(),
                replay,
                cashflowReconciliation.pending(batchId));
    }

    private static String analysisState(String status) {
        if (status == null) return "PORTFOLIO_READY";
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
                .param("runId", runId == null ? null : runId.toString())
                .param("openPositions", result.openPositionCount())
                .param("closedPositions", result.closedPositionCount())
                .param("now", clock.instant())
                .update();
    }

    public record ConfirmCommand(
            long expectedVersion,
            List<AccountMapping> accountMappings,
            List<RowOverride> rowOverrides,
            CashSetup cashSetup) {
        public ConfirmCommand {
            accountMappings = accountMappings == null ? List.of() : List.copyOf(accountMappings);
            rowOverrides = rowOverrides == null ? List.of() : List.copyOf(rowOverrides);
            if (cashSetup == null) throw new IllegalArgumentException("Safety-cash setup is required");
        }
    }

    public record AccountMapping(String accountNumberMasked, UUID existingAccountId, String displayName) {}

    public record RowOverride(
            int rowNumber, String symbol, String assetType, String rowType, String classification, boolean ignored) {}

    public enum CashLocation {
        IN_FIDELITY,
        EXTERNAL_BANK,
        SPLIT,
        BELOW_TARGET
    }

    public record CashSetup(
            CashLocation location, java.math.BigDecimal fidelityAmount, java.math.BigDecimal externalAmount) {
        public CashSetup {
            if (location == null) throw new IllegalArgumentException("Safety-cash location is required");
            if (fidelityAmount == null || externalAmount == null) {
                throw new IllegalArgumentException("Both Fidelity and external safety-cash amounts are required");
            }
            if (fidelityAmount.signum() < 0 || externalAmount.signum() < 0) {
                throw new IllegalArgumentException("Safety-cash amounts cannot be negative");
            }
        }

        public java.math.BigDecimal totalAmount() {
            return fidelityAmount.add(externalAmount);
        }

        public void validateAgainst(java.math.BigDecimal importedBrokerCash, java.math.BigDecimal emergencyCashFloor) {
            if (fidelityAmount.compareTo(importedBrokerCash) > 0) {
                throw new IllegalArgumentException("Fidelity safety cash exceeds imported Fidelity cash");
            }
            switch (location) {
                case IN_FIDELITY -> {
                    if (externalAmount.signum() != 0) {
                        throw new IllegalArgumentException("IN_FIDELITY requires external amount to be zero");
                    }
                }
                case EXTERNAL_BANK -> {
                    if (fidelityAmount.signum() != 0) {
                        throw new IllegalArgumentException("EXTERNAL_BANK requires Fidelity amount to be zero");
                    }
                }
                case SPLIT -> {
                    if (fidelityAmount.signum() <= 0 || externalAmount.signum() <= 0) {
                        throw new IllegalArgumentException("SPLIT requires positive Fidelity and external amounts");
                    }
                }
                case BELOW_TARGET -> {
                    if (totalAmount().compareTo(emergencyCashFloor) >= 0) {
                        throw new IllegalArgumentException(
                                "BELOW_TARGET total must remain below the emergency-cash target");
                    }
                }
            }
        }
    }

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
            boolean idempotentReplay,
            PortfolioCashflowReconciliationService.Result cashflowReconciliation) {}

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
