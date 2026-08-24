package com.example.portfolio.portfolio;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
    private final PublishedStrategyService strategies;

    public PositionIntelligenceStore(JdbcClient jdbc, Clock clock, PublishedStrategyService strategies) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.strategies = strategies;
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
                               j.mfe_r, j.mae_r, j.exit_reason, j.notes, j.reason_tags, j.occurred_at
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

    public ChartView chart(String email, UUID positionId, LocalDate from) {
        var owned = jdbc.sql(
                        """
                        SELECT COUNT(*) FROM position p JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        WHERE u.email=:email AND p.id=UUID_TO_BIN(:positionId)
                        """)
                .param("email", email)
                .param("positionId", positionId.toString())
                .query(Integer.class)
                .single();
        if (owned != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var bars = jdbc.sql(
                        """
                        WITH ranked AS (
                            SELECT b.market_date, b.open_price, b.high_price, b.low_price, b.close_price,
                                   b.quality_status, b.data_as_of,
                                   ROW_NUMBER() OVER (PARTITION BY b.market_date ORDER BY b.data_as_of DESC,b.created_at DESC) rn
                            FROM price_bar b JOIN position p ON p.instrument_id=b.instrument_id
                            WHERE p.id=UUID_TO_BIN(:positionId) AND b.adjusted=TRUE AND b.timeframe='1D'
                              AND b.market_date>=:from AND b.market_date<=CURRENT_DATE
                        )
                        SELECT market_date marketDate,open_price open,high_price high,low_price low,
                               close_price close,quality_status quality,data_as_of dataAsOf
                        FROM ranked WHERE rn=1 ORDER BY market_date
                        """)
                .param("positionId", positionId.toString())
                .param("from", from)
                .query(ChartBarView.class)
                .list();
        var entries = jdbc.sql(
                        """
                        SELECT DATE(p.opened_at) marketDate,p.average_cost price,'AVERAGE_COST' markerType
                        FROM position p WHERE p.id=UUID_TO_BIN(:positionId) AND p.average_cost IS NOT NULL
                        """)
                .param("positionId", positionId.toString())
                .query(ChartMarkerView.class)
                .list();
        var stops = jdbc.sql(
                        """
                        WITH ranked AS (
                            SELECT DATE(data_as_of) market_date, initial_stop, live_stop, soft_alert,
                                   ROW_NUMBER() OVER (
                                       PARTITION BY DATE(data_as_of)
                                       ORDER BY data_as_of DESC, created_at DESC
                                   ) rn
                            FROM stop_snapshot
                            WHERE position_id=UUID_TO_BIN(:positionId) AND DATE(data_as_of)>=:from
                        )
                        SELECT market_date marketDate, initial_stop formalStop,
                               live_stop liveStop, soft_alert softAlert
                        FROM ranked WHERE rn=1 ORDER BY market_date
                        """)
                .param("positionId", positionId.toString())
                .param("from", from)
                .query(StopSeriesView.class)
                .list();
        var events = jdbc.sql(
                        """
                        SELECT e.market_date marketDate,e.event_type markerType,e.title label
                        FROM company_event e JOIN position p ON p.instrument_id=e.instrument_id
                        WHERE p.id=UUID_TO_BIN(:positionId) AND e.market_date>=:from ORDER BY e.market_date
                        """)
                .param("positionId", positionId.toString())
                .param("from", from)
                .query(EventMarkerView.class)
                .list();
        var trades = jdbc.sql(
                        """
                        SELECT DATE(occurred_at) marketDate,entry_type markerType,
                               COALESCE(notes,entry_type) label FROM trade_journal
                        WHERE position_id=UUID_TO_BIN(:positionId) AND DATE(occurred_at)>=:from ORDER BY occurred_at
                        """)
                .param("positionId", positionId.toString())
                .param("from", from)
                .query(EventMarkerView.class)
                .list();
        var quality = bars.isEmpty()
                ? "MISSING"
                : bars.stream().anyMatch(value -> !"HEALTHY".equals(value.quality())) ? "PARTIAL" : "HEALTHY";
        var dataAsOf = bars.stream()
                .map(ChartBarView::dataAsOf)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new ChartView(bars, entries, stops, events, trades, dataAsOf, quality);
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
                            :strategyVersion, JSON_ARRAY('THESIS.CONFIRM.001'),
                            JSON_OBJECT('previousVersion', :version), :now
                        FROM app_user u WHERE u.email = :email
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("entityId", positionId.toString())
                .param("version", expectedVersion)
                .param("strategyVersion", strategies.current().version())
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
            String reasonTags,
            LocalDateTime occurredAt) {}

    public record ChartView(
            List<ChartBarView> bars,
            List<ChartMarkerView> entryMarkers,
            List<StopSeriesView> stopSeries,
            List<EventMarkerView> earningsMarkers,
            List<EventMarkerView> tradeMarkers,
            LocalDateTime dataAsOf,
            String quality) {}

    public record ChartBarView(
            LocalDate marketDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            String quality,
            LocalDateTime dataAsOf) {}

    public record ChartMarkerView(LocalDate marketDate, BigDecimal price, String markerType) {}

    public record StopSeriesView(
            LocalDate marketDate, BigDecimal formalStop, BigDecimal liveStop, BigDecimal softAlert) {}

    public record EventMarkerView(LocalDate marketDate, String markerType, String label) {}
}
