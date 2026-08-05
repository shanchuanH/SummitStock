package com.example.portfolio.portfolio;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class PositionIntelligenceStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    public PositionIntelligenceStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Optional<StopView> latestStop(String email, UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(s.id) id, s.strategy_version, s.entry_price, s.atr,
                               s.initial_stop, s.live_stop, s.soft_alert, s.catastrophic_stop,
                               s.close_confirmed, s.rule_ids, s.quality_status, s.data_as_of
                        FROM stop_snapshot s
                        JOIN position p ON p.id = s.position_id
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        WHERE u.email = :email AND p.id = UUID_TO_BIN(:positionId)
                        ORDER BY s.data_as_of DESC LIMIT 1
                        """)
                .param("email", email)
                .param("positionId", positionId.toString())
                .query(StopView.class)
                .optional();
    }

    public Optional<ThesisView> thesis(String email, UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(t.id) id, t.summary, t.confirmation_signals,
                               t.invalidation_signals, t.status, t.expires_at,
                               t.user_confirmed, t.confirmed_at, t.version,
                               COALESCE((SELECT JSON_ARRAYAGG(ts.source_uri) FROM thesis_source ts WHERE ts.thesis_id = t.id), JSON_ARRAY()) sources
                        FROM position_thesis t
                        JOIN position p ON p.id = t.position_id
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        WHERE u.email = :email AND p.id = UUID_TO_BIN(:positionId)
                        """)
                .param("email", email)
                .param("positionId", positionId.toString())
                .query(ThesisView.class)
                .optional();
    }

    public Optional<ValuationView> latestValuation(String email, UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT v.fundamental_health, v.valuation_discount, v.earnings_revisions,
                               v.price_stabilization, v.portfolio_capacity, v.discount_tactical_weight,
                               v.action, v.rule_ids, v.strategy_version, v.data_as_of, v.valid_until
                        FROM valuation_snapshot v
                        JOIN position p ON p.id = v.position_id
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        WHERE u.email = :email AND p.id = UUID_TO_BIN(:positionId)
                        ORDER BY v.data_as_of DESC LIMIT 1
                        """)
                .param("email", email)
                .param("positionId", positionId.toString())
                .query(ValuationView.class)
                .optional();
    }

    public Optional<EarningsView> latestEarnings(String email, UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT e.event_count, e.next_event_at, e.downside_tail_fraction,
                               e.gap_p75_fraction, e.gap_p90_fraction, e.profit_cushion_r,
                               e.action, e.rule_ids, e.strategy_version, e.data_as_of, e.valid_until
                        FROM earnings_risk_snapshot e
                        JOIN position p ON p.id = e.position_id
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        WHERE u.email = :email AND p.id = UUID_TO_BIN(:positionId)
                        ORDER BY e.data_as_of DESC LIMIT 1
                        """)
                .param("email", email)
                .param("positionId", positionId.toString())
                .query(EarningsView.class)
                .optional();
    }

    public List<JournalView> journal(String email, UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(j.id) id, j.entry_type, j.tax_status,
                               j.planned_risk_amount, j.planned_r, j.realized_r,
                               j.mfe_r, j.mae_r, j.exit_reason, j.notes, j.occurred_at
                        FROM trade_journal j
                        JOIN app_user u ON u.id = j.user_id
                        WHERE u.email = :email AND j.position_id = UUID_TO_BIN(:positionId)
                        ORDER BY j.occurred_at DESC
                        """)
                .param("email", email)
                .param("positionId", positionId.toString())
                .query(JournalView.class)
                .list();
    }

    @Transactional
    public ThesisView confirmThesis(String email, UUID positionId, long expectedVersion) {
        var current = thesis(email, positionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (current.version() != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Thesis version changed");
        var updated = jdbc.sql(
                        """
                        UPDATE position_thesis t
                        JOIN position p ON p.id = t.position_id
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        SET t.user_confirmed = TRUE, t.confirmed_at = :now,
                            t.updated_at = :now, t.version = t.version + 1
                        WHERE u.email = :email AND p.id = UUID_TO_BIN(:positionId) AND t.version = :version
                        """)
                .param("now", clock.instant())
                .param("email", email)
                .param("positionId", positionId.toString())
                .param("version", expectedVersion)
                .update();
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Thesis version changed");
        jdbc.sql(
                        """
                        INSERT INTO audit_log (id, user_id, event_type, entity_type, entity_id,
                            strategy_version, rule_ids, details, occurred_at)
                        SELECT UUID_TO_BIN(:id), u.id, 'THESIS_CONFIRMED', 'POSITION', :entityId,
                            '1.0.0-draft', JSON_ARRAY('THESIS.CONFIRM.001'),
                            JSON_OBJECT('previousVersion', :version), :now
                        FROM app_user u WHERE u.email = :email
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("entityId", positionId.toString())
                .param("version", expectedVersion)
                .param("now", clock.instant())
                .param("email", email)
                .update();
        return thesis(email, positionId).orElseThrow();
    }

    public record StopView(
            UUID id,
            String strategyVersion,
            BigDecimal entryPrice,
            BigDecimal atr,
            BigDecimal initialStop,
            BigDecimal liveStop,
            BigDecimal softAlert,
            BigDecimal catastrophicStop,
            boolean closeConfirmed,
            String ruleIds,
            String qualityStatus,
            LocalDateTime dataAsOf) {}

    public record ThesisView(
            UUID id,
            String summary,
            String confirmationSignals,
            String invalidationSignals,
            String status,
            LocalDateTime expiresAt,
            boolean userConfirmed,
            LocalDateTime confirmedAt,
            long version,
            String sources) {}

    public record ValuationView(
            String fundamentalHealth,
            boolean valuationDiscount,
            String earningsRevisions,
            String priceStabilization,
            boolean portfolioCapacity,
            BigDecimal discountTacticalWeight,
            String action,
            String ruleIds,
            String strategyVersion,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil) {}

    public record EarningsView(
            int eventCount,
            LocalDateTime nextEventAt,
            BigDecimal downsideTailFraction,
            BigDecimal gapP75Fraction,
            BigDecimal gapP90Fraction,
            BigDecimal profitCushionR,
            String action,
            String ruleIds,
            String strategyVersion,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil) {}

    public record JournalView(
            UUID id,
            String entryType,
            String taxStatus,
            BigDecimal plannedRiskAmount,
            BigDecimal plannedR,
            BigDecimal realizedR,
            BigDecimal mfeR,
            BigDecimal maeR,
            String exitReason,
            String notes,
            LocalDateTime occurredAt) {}
}
