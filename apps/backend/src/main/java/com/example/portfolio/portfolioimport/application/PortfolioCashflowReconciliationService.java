package com.example.portfolio.portfolioimport.application;

import com.example.portfolio.analysis.risk.PortfolioNavService;
import java.math.BigDecimal;
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
public class PortfolioCashflowReconciliationService {
    private static final BigDecimal MIN_TOLERANCE = new BigDecimal("5.00");
    private static final BigDecimal VALUE_TOLERANCE = new BigDecimal("0.001");

    private final JdbcClient jdbc;
    private final PortfolioNavService nav;
    private final Clock clock;

    public PortfolioCashflowReconciliationService(JdbcClient jdbc, PortfolioNavService nav, Clock clock) {
        this.jdbc = jdbc;
        this.nav = nav;
        this.clock = clock;
    }

    public Snapshot before(UUID userId) {
        return snapshot(userId);
    }

    public Result reconcile(UUID userId, UUID batchId, Snapshot before) {
        var after = snapshot(userId);
        var reliableTradeValue = reliableTradeValue(userId, before.capturedAt(), after.capturedAt());
        var assessment = assess(before, after, reliableTradeValue);
        var cashChange = assessment.cashChange();
        if (!"REQUIRED".equals(assessment.status())) {
            return new Result(null, assessment.status(), cashChange);
        }
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO portfolio_cashflow_reconciliation (
                          id,user_id,import_batch_id,prior_cash,current_cash,cash_change,net_position_trade_value,
                          broker_value,status,created_at,updated_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),UUID_TO_BIN(:batchId),:priorCash,:currentCash,
                          :change,:tradeValue,:brokerValue,'REQUIRED',:now,:now)
                        ON DUPLICATE KEY UPDATE prior_cash=VALUES(prior_cash),current_cash=VALUES(current_cash),
                          cash_change=VALUES(cash_change),net_position_trade_value=VALUES(net_position_trade_value),
                          broker_value=VALUES(broker_value),status='REQUIRED',confirmation_type=NULL,updated_at=VALUES(updated_at)
                        """)
                .param("id", id.toString())
                .param("userId", userId.toString())
                .param("batchId", batchId.toString())
                .param("priorCash", before.brokerCash())
                .param("currentCash", after.brokerCash())
                .param("change", cashChange)
                .param("tradeValue", reliableTradeValue == null ? BigDecimal.ZERO : reliableTradeValue)
                .param("brokerValue", after.brokerValue())
                .param("now", clock.instant())
                .update();
        auditRequired(userId, batchId, cashChange, reliableTradeValue == null ? BigDecimal.ZERO : reliableTradeValue);
        return pending(batchId);
    }

    static Assessment assess(Snapshot before, Snapshot after) {
        return assess(before, after, null);
    }

    static Assessment assess(Snapshot before, Snapshot after, BigDecimal reliableNetPurchaseValue) {
        var cashChange = after.brokerCash().subtract(before.brokerCash());
        if (!before.rawCashEstablished()) return new Assessment("BASELINE_ESTABLISHED", cashChange);
        if (cashChange.signum() == 0) return new Assessment("NO_CASH_CHANGE", BigDecimal.ZERO);
        var tolerance = after.brokerValue().multiply(VALUE_TOLERANCE).max(MIN_TOLERANCE);
        if (reliableNetPurchaseValue != null
                && cashChange.add(reliableNetPurchaseValue).abs().compareTo(tolerance) <= 0) {
            return new Assessment("RECONCILED_INTERNAL_TRADE", cashChange);
        }
        return new Assessment("REQUIRED", cashChange);
    }

    @Transactional
    public Result confirm(String email, UUID batchId, ConfirmationType type) {
        var row = jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(c.id) id,BIN_TO_UUID(c.user_id) userId,c.cash_change cashChange,c.status,
                               DATE(COALESCE(b.data_as_of,b.created_at)) effectiveDate
                        FROM portfolio_cashflow_reconciliation c JOIN app_user u ON u.id=c.user_id
                        JOIN portfolio_import_batch b ON b.id=c.import_batch_id
                        WHERE c.import_batch_id=UUID_TO_BIN(:batchId) AND u.email=:email
                        """)
                .param("batchId", batchId.toString())
                .param("email", email)
                .query(PendingRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"REQUIRED".equals(row.status())) return new Result(row.id(), row.status(), row.cashChange());
        var status =
                switch (type) {
                    case EXTERNAL_CASHFLOW -> "RECORDED_EXTERNAL_CASHFLOW";
                    case INTERNAL_TRADE -> "CONFIRMED_INTERNAL_TRADE";
                    case OTHER -> "REQUIRED";
                };
        if (type == ConfirmationType.EXTERNAL_CASHFLOW) {
            nav.recordExternalCashflow(
                    row.userId(),
                    row.effectiveDate(),
                    row.cashChange(),
                    "USER_CONFIRMED_BROKER_CASHFLOW",
                    batchId.toString());
        }
        jdbc.sql(
                        """
                        UPDATE portfolio_cashflow_reconciliation
                        SET status=:status,confirmation_type=:type,confirmed_at=:confirmedAt,updated_at=:confirmedAt
                        WHERE id=UUID_TO_BIN(:id)
                        """)
                .param("status", status)
                .param("type", type.name())
                .param("confirmedAt", clock.instant())
                .param("id", row.id().toString())
                .update();
        return new Result(row.id(), status, row.cashChange());
    }

    public Result pending(UUID batchId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) reconciliationId,status,cash_change cashChange
                        FROM portfolio_cashflow_reconciliation WHERE import_batch_id=UUID_TO_BIN(:batchId)
                        """)
                .param("batchId", batchId.toString())
                .query(Result.class)
                .optional()
                .orElse(new Result(null, "NONE", BigDecimal.ZERO));
    }

    private BigDecimal reliableTradeValue(UUID userId, java.time.Instant afterExclusive, java.time.Instant through) {
        if (afterExclusive == null || through == null || !through.isAfter(afterExclusive)) return null;
        return jdbc.sql(
                        """
                        SELECT SUM(quantity_delta*execution_price) FROM trade_journal
                        WHERE user_id=UUID_TO_BIN(:userId) AND quantity_delta IS NOT NULL AND execution_price IS NOT NULL
                          AND occurred_at>:afterExclusive AND occurred_at<=:through
                        """)
                .param("userId", userId.toString())
                .param("afterExclusive", afterExclusive)
                .param("through", through)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    private Snapshot snapshot(UUID userId) {
        var rawCash = jdbc.sql(
                        """
                        SELECT COUNT(*) evidenceCount,COALESCE(SUM(s.cash_amount),0) brokerCash
                        FROM broker_cash_snapshot s
                        WHERE s.import_batch_id=(
                          SELECT latest.import_batch_id FROM broker_cash_snapshot latest
                          WHERE latest.user_id=UUID_TO_BIN(:userId) AND latest.source='FIDELITY_CSV'
                          ORDER BY latest.data_as_of DESC,latest.created_at DESC LIMIT 1
                        )
                        """)
                .param("userId", userId.toString())
                .query(RawCashTotal.class)
                .single();
        var positions = jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.account_id) accountId,BIN_TO_UUID(p.instrument_id) instrumentId,
                               p.quantity,p.market_value marketValue
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND a.import_source='FIDELITY_CSV' AND p.status='OPEN'
                        """)
                .param("userId", userId.toString())
                .query(PositionBalance.class)
                .list();
        var holdings = positions.stream()
                .map(PositionBalance::marketValue)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Snapshot(
                rawCash.brokerCash(),
                rawCash.brokerCash().add(holdings),
                rawCash.evidenceCount() > 0,
                positions,
                clock.instant());
    }

    private void auditRequired(UUID userId, UUID batchId, BigDecimal cashChange, BigDecimal tradeValue) {
        jdbc.sql(
                        """
                        INSERT INTO audit_log (id,user_id,event_type,entity_type,entity_id,rule_ids,details,occurred_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),'NAV_RECONCILIATION_REQUIRED','PORTFOLIO_IMPORT_BATCH',:batchId,
                                JSON_ARRAY('NAV.CASHFLOW.AMBIGUOUS.001'),
                                JSON_OBJECT('cashChange',:change,'netPositionTradeValue',:tradeValue),:now)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("batchId", batchId.toString())
                .param("change", cashChange)
                .param("tradeValue", tradeValue)
                .param("now", clock.instant())
                .update();
    }

    public enum ConfirmationType {
        EXTERNAL_CASHFLOW,
        INTERNAL_TRADE,
        OTHER
    }

    public record Snapshot(
            BigDecimal brokerCash,
            BigDecimal brokerValue,
            boolean rawCashEstablished,
            List<PositionBalance> positions,
            java.time.Instant capturedAt) {
        public Snapshot(
                BigDecimal brokerCash,
                BigDecimal brokerValue,
                boolean rawCashEstablished,
                List<PositionBalance> positions) {
            this(brokerCash, brokerValue, rawCashEstablished, positions, java.time.Instant.EPOCH);
        }
    }

    public record PositionBalance(UUID accountId, UUID instrumentId, BigDecimal quantity, BigDecimal marketValue) {
        public PositionBalance(UUID instrumentId, BigDecimal quantity, BigDecimal marketValue) {
            this(null, instrumentId, quantity, marketValue);
        }

        BalanceKey key() {
            return new BalanceKey(accountId, instrumentId);
        }
    }

    record BalanceKey(UUID accountId, UUID instrumentId) {}

    record Assessment(String status, BigDecimal cashChange) {}

    private record PendingRow(UUID id, UUID userId, BigDecimal cashChange, String status, LocalDate effectiveDate) {}

    private record RawCashTotal(long evidenceCount, BigDecimal brokerCash) {}

    public record Result(UUID reconciliationId, String status, BigDecimal cashChange) {}
}
