package com.example.portfolio.portfolio;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class DipCashflowStore {
    private final JdbcClient jdbc;

    public DipCashflowStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<DipView> latestDip(String email) {
        return jdbc.sql(
                        """
                SELECT BIN_TO_UUID(d.id) id, i.symbol, d.strategy_version, d.status,
                       d.setup_score, d.trigger_count, d.trigger_codes, d.tranche_index, d.tranche_pct,
                       d.portfolio_drawdown, d.instrument_drawdown, d.market_driven,
                       d.emergency_cash_protected, d.reserve_before, d.reserve_after, d.quality,
                       d.rule_ids, d.data_as_of, d.valid_until
                FROM etf_dip_event d JOIN app_user u ON u.id=d.user_id JOIN instrument i ON i.id=d.instrument_id
                WHERE u.email=:email ORDER BY d.data_as_of DESC LIMIT 1
                """)
                .param("email", email)
                .query(DipView.class)
                .optional();
    }

    public List<HistoryView> recommendationHistory(String email) {
        return jdbc.sql(
                        """
                SELECT BIN_TO_UUID(r.id) id, i.symbol, r.action, r.priority, r.confidence,
                       r.strategy_version, r.rule_ids, r.data_as_of, r.valid_until, r.status,
                       ack.decision_type decisionType,ack.rationale,ack.acknowledged_at acknowledgedAt,
                       initial.current_weight initialWeight,latest.current_weight currentWeight,
                       (SELECT m.decision_price FROM position_mark_snapshot m WHERE m.position_id=r.position_id AND m.data_as_of<=r.data_as_of ORDER BY m.market_date DESC,m.data_as_of DESC LIMIT 1) decisionPrice,
                       (SELECT m.decision_price FROM current_position_mark m WHERE m.position_id=r.position_id) currentPrice
                FROM recommendation r JOIN app_user u ON u.id=r.user_id
                LEFT JOIN position p ON p.id=r.position_id LEFT JOIN instrument i ON i.id=p.instrument_id
                LEFT JOIN holding_analysis_snapshot initial ON initial.id=r.holding_analysis_id
                LEFT JOIN holding_analysis_snapshot latest ON latest.id=(SELECT h.id FROM holding_analysis_snapshot h WHERE h.position_id=r.position_id ORDER BY h.data_as_of DESC,h.created_at DESC LIMIT 1)
                LEFT JOIN recommendation_acknowledgement ack ON ack.id=(SELECT a.id FROM recommendation_acknowledgement a WHERE a.recommendation_id=r.id AND a.user_id=r.user_id ORDER BY a.acknowledged_at DESC LIMIT 1)
                WHERE u.email=:email ORDER BY r.created_at DESC LIMIT 100
                """)
                .param("email", email)
                .query(HistoryView.class)
                .list();
    }

    public record DipView(
            UUID id,
            String symbol,
            String strategyVersion,
            String status,
            double setupScore,
            int triggerCount,
            String triggerCodes,
            Integer trancheIndex,
            BigDecimal tranchePct,
            BigDecimal portfolioDrawdown,
            BigDecimal instrumentDrawdown,
            boolean marketDriven,
            boolean emergencyCashProtected,
            BigDecimal reserveBefore,
            BigDecimal reserveAfter,
            String quality,
            String ruleIds,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil) {}

    public record HistoryView(
            UUID id,
            String symbol,
            String action,
            String priority,
            String confidence,
            String strategyVersion,
            String ruleIds,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil,
            String status,
            String decisionType,
            String rationale,
            LocalDateTime acknowledgedAt,
            BigDecimal initialWeight,
            BigDecimal currentWeight,
            BigDecimal decisionPrice,
            BigDecimal currentPrice) {}
}
