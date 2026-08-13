package com.example.portfolio.portfolioimport.application;

import com.example.portfolio.analysis.risk.PortfolioNavService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PortfolioCashflowReconciliationService {
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
        var change = after.brokerCash().subtract(before.brokerCash());
        if (!before.portfolioEstablished()) return new Result("BASELINE_ESTABLISHED", change);
        if (change.signum() == 0) return new Result("NO_CASH_CHANGE", BigDecimal.ZERO);
        if (before.positionFingerprint().equals(after.positionFingerprint())) {
            nav.recordExternalCashflow(
                    userId, LocalDate.now(clock), change, "BROKER_IMPORT_RECONCILIATION", batchId.toString());
            return new Result("RECORDED", change);
        }
        jdbc.sql(
                        """
                        INSERT INTO audit_log (id,user_id,event_type,entity_type,entity_id,rule_ids,details,occurred_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),'NAV_RECONCILIATION_REQUIRED','PORTFOLIO_IMPORT_BATCH',:batchId,
                                JSON_ARRAY('NAV.CASHFLOW.AMBIGUOUS.001'),JSON_OBJECT('cashChange',:change),:now)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("batchId", batchId.toString())
                .param("change", change)
                .param("now", clock.instant())
                .update();
        return new Result("NAV_RECONCILIATION_REQUIRED", change);
    }

    private Snapshot snapshot(UUID userId) {
        var cash = jdbc.sql(
                        """
                        SELECT COALESCE(SUM(c.current_amount),0) FROM cash_bucket c
                        JOIN investment_account a ON a.id=c.account_id
                        WHERE c.user_id=UUID_TO_BIN(:userId) AND c.bucket_type='ALLOCATED_TRADE'
                          AND a.import_source='FIDELITY_CSV'
                        """)
                .param("userId", userId.toString())
                .query(BigDecimal.class)
                .single();
        var fingerprint = jdbc.sql(
                        """
                        SELECT COALESCE(SHA2(GROUP_CONCAT(CONCAT(BIN_TO_UUID(p.instrument_id),':',p.quantity)
                          ORDER BY p.instrument_id SEPARATOR '|'),256),'EMPTY')
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        """)
                .param("userId", userId.toString())
                .query(String.class)
                .single();
        return new Snapshot(cash, !"EMPTY".equals(fingerprint), fingerprint);
    }

    public record Snapshot(BigDecimal brokerCash, boolean portfolioEstablished, String positionFingerprint) {}

    public record Result(String status, BigDecimal cashChange) {}
}
