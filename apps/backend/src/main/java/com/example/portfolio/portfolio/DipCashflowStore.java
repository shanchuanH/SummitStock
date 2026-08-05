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
                       d.setup_score, d.trigger_count, d.portfolio_drawdown, d.market_driven,
                       d.emergency_cash_protected, d.rule_ids, d.data_as_of, d.valid_until
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
                       r.strategy_version, r.rule_ids, r.data_as_of, r.valid_until, r.status
                FROM recommendation r JOIN app_user u ON u.id=r.user_id
                LEFT JOIN position p ON p.id=r.position_id LEFT JOIN instrument i ON i.id=p.instrument_id
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
            BigDecimal portfolioDrawdown,
            boolean marketDriven,
            boolean emergencyCashProtected,
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
            String status) {}
}
