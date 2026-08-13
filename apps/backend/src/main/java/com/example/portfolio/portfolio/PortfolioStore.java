package com.example.portfolio.portfolio;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.analysis.capital.CapitalBaseService;
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
public class PortfolioStore {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final CapitalBaseService capitalBases;
    private final PublishedStrategyService strategies;

    public PortfolioStore(
            JdbcClient jdbc, Clock clock, CapitalBaseService capitalBases, PublishedStrategyService strategies) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.capitalBases = capitalBases;
        this.strategies = strategies;
    }

    public List<AccountView> accounts(String email) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(a.id) id, a.institution, a.account_type,
                               a.display_name, a.currency, a.active, a.version
                        FROM investment_account a
                        JOIN app_user u ON u.id = a.user_id
                        WHERE u.email = :email AND a.active = TRUE
                        ORDER BY a.display_name
                        """)
                .param("email", email)
                .query(AccountView.class)
                .list();
    }

    public PortfolioSummaryView summary(String email) {
        return jdbc.sql(
                        """
                        SELECT
                            COALESCE((SELECT SUM(m.marked_market_value)
                                      FROM position p
                                      JOIN investment_account a ON a.id = p.account_id
                                      JOIN app_user u ON u.id = a.user_id
                                      JOIN current_position_mark m ON m.position_id=p.id
                                      WHERE u.email = :email AND p.status = 'OPEN'), 0) invested_value,
                            COALESCE((SELECT SUM(c.current_amount)
                                      FROM cash_bucket c JOIN app_user u ON u.id = c.user_id
                                      WHERE u.email = :email), 0) tracked_cash,
                            COALESCE((SELECT COUNT(*)
                                      FROM position p
                                      JOIN investment_account a ON a.id = p.account_id
                                      JOIN app_user u ON u.id = a.user_id
                                      WHERE u.email = :email AND p.status = 'OPEN'), 0) open_positions,
                            (SELECT MAX(m.data_as_of)
                             FROM current_position_mark m
                             JOIN position p ON p.id=m.position_id
                             JOIN investment_account a ON a.id = p.account_id
                             JOIN app_user u ON u.id = a.user_id
                             WHERE u.email = :email) data_as_of
                        """)
                .param("email", email)
                .query(PortfolioSummaryView.class)
                .single();
    }

    public List<PositionView> positions(String email) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) id, BIN_TO_UUID(p.account_id) account_id,
                               i.symbol, p.bucket, p.classification, p.classification_confirmed,
                               p.quantity, p.average_cost, COALESCE(m.marked_market_value,0) market_value,
                               p.status, p.version
                        FROM position p
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        JOIN instrument i ON i.id = p.instrument_id
                        LEFT JOIN current_position_mark m ON m.position_id=p.id
                        WHERE u.email = :email
                        ORDER BY p.status, i.symbol, p.bucket
                        """)
                .param("email", email)
                .query(PositionView.class)
                .list();
    }

    public Optional<PositionView> position(String email, UUID id) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) id, BIN_TO_UUID(p.account_id) account_id,
                               i.symbol, p.bucket, p.classification, p.classification_confirmed,
                               p.quantity, p.average_cost, COALESCE(m.marked_market_value,0) market_value,
                               p.status, p.version
                        FROM position p
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        JOIN instrument i ON i.id = p.instrument_id
                        LEFT JOIN current_position_mark m ON m.position_id=p.id
                        WHERE u.email = :email AND p.id = UUID_TO_BIN(:id)
                        """)
                .param("email", email)
                .param("id", id.toString())
                .query(PositionView.class)
                .optional();
    }

    public List<PortfolioHoldingView> holdings(String email) {
        var investable = capitalBases.calculate(userId(email)).investableAssets();
        return jdbc.sql(
                        """
                        WITH owned AS (
                            SELECT p.*, COALESCE(m.marked_market_value,0) canonical_market_value,
                                   i.symbol, i.asset_type,
                                   COALESCE(JSON_UNQUOTE(JSON_EXTRACT(i.metadata,'$.name')),i.symbol) instrument_name,
                                   a.user_id
                            FROM position p
                            JOIN investment_account a ON a.id=p.account_id
                            JOIN app_user u ON u.id=a.user_id
                            JOIN instrument i ON i.id=p.instrument_id
                            LEFT JOIN current_position_mark m ON m.position_id=p.id
                            WHERE u.email=:email AND p.status='OPEN'
                        )
                        SELECT BIN_TO_UUID(o.id) id, o.version, o.symbol, o.instrument_name name, o.asset_type assetType,
                               o.bucket, o.classification, o.classification_confirmed classificationConfirmed,
                               o.canonical_market_value marketValue,
                               CASE WHEN :investable=0 THEN 0 ELSE o.canonical_market_value/:investable END currentWeight,
                               h.target_weight_min targetWeightMin, h.target_weight_max targetWeightMax,
                               COALESCE(r.action,h.recommended_action,'WAIT_FOR_DATA') action,
                               COALESCE(r.priority,'WATCH') priority,
                               COALESCE(r.confidence,h.confidence,'WAIT_FOR_DATA') confidence,
                               CASE WHEN q.last_price IS NULL OR sma.value_double IS NULL THEN 'WAIT_FOR_DATA'
                                    WHEN q.last_price>=sma.value_double THEN 'ABOVE_TREND' ELSE 'BELOW_TREND' END trend,
                               (SELECT MIN(e.event_at) FROM company_event e
                                WHERE e.instrument_id=o.instrument_id AND e.event_at>=UTC_TIMESTAMP(6)) nextEvent,
                               COALESCE(h.readiness,o.data_readiness,'WAIT_FOR_DATA') dataStatus
                        FROM owned o
                        LEFT JOIN holding_analysis_snapshot h ON h.id=(
                            SELECT x.id FROM holding_analysis_snapshot x WHERE x.position_id=o.id
                            ORDER BY x.data_as_of DESC,x.created_at DESC LIMIT 1)
                        LEFT JOIN recommendation r ON r.id=(
                            SELECT y.id FROM recommendation y WHERE y.position_id=o.id AND y.status='ACTIVE'
                            ORDER BY y.data_as_of DESC,y.created_at DESC LIMIT 1)
                        LEFT JOIN quote q ON q.id=(
                            SELECT z.id FROM quote z WHERE z.instrument_id=o.instrument_id
                            ORDER BY z.data_as_of DESC,z.created_at DESC LIMIT 1)
                        LEFT JOIN indicator_snapshot sma ON sma.id=(
                            SELECT s.id FROM indicator_snapshot s WHERE s.instrument_id=o.instrument_id
                              AND s.indicator_code='SMA_20' AND s.status='READY'
                            ORDER BY s.market_date DESC,s.created_at DESC LIMIT 1)
                        ORDER BY FIELD(COALESCE(r.priority,'WATCH'),'MUST_ACT','DO_NOT','WATCH','NORMAL'),o.symbol
                        """)
                .param("email", email)
                .param("investable", investable)
                .query(PortfolioHoldingView.class)
                .list();
    }

    private UUID userId(String email) {
        return jdbc.sql("SELECT BIN_TO_UUID(id) FROM app_user WHERE email=:email")
                .param("email", email)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public ClassificationEvidence classificationEvidence(String email, UUID id) {
        return jdbc.sql(
                        """
                        SELECT i.symbol, i.asset_type assetType,
                               COALESCE((SELECT x.thematic FROM instrument_analysis_profile x
                                         WHERE x.instrument_id=i.id ORDER BY x.data_as_of DESC LIMIT 1), FALSE) thematic,
                               (p.classification='UNVESTED_COMPENSATION') unvestedCompensation
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        JOIN instrument i ON i.id=p.instrument_id
                        WHERE u.email=:email AND p.id=UUID_TO_BIN(:id)
                        """)
                .param("email", email)
                .param("id", id.toString())
                .query(ClassificationEvidence.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @Transactional
    public PositionView confirmClassification(
            String email, UUID id, String classification, String source, long expectedVersion) {
        var current = position(email, id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (current.version() != expectedVersion) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Position version changed");
        }
        int updated = jdbc.sql(
                        """
                        UPDATE position p
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        SET p.classification = :classification,
                            p.bucket = :bucket,
                            p.classification_confirmed = TRUE,
                            p.classification_source = :source,
                            p.updated_at = :updatedAt,
                            p.version = p.version + 1
                        WHERE p.id = UUID_TO_BIN(:id) AND u.email = :email AND p.version = :version
                        """)
                .param("classification", classification)
                .param("bucket", bucketFor(classification))
                .param("source", source)
                .param("updatedAt", clock.instant())
                .param("id", id.toString())
                .param("email", email)
                .param("version", expectedVersion)
                .update();
        if (updated != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Position version changed");
        jdbc.sql(
                        """
                        INSERT INTO audit_log (
                            id, user_id, event_type, entity_type, entity_id,
                            strategy_version, rule_ids, details, occurred_at
                        )
                        SELECT UUID_TO_BIN(:auditId), u.id, 'POSITION_CLASSIFIED', 'POSITION', :entityId,
                               :strategyVersion, JSON_ARRAY('POSITION.CLASSIFY.001'),
                               JSON_OBJECT('classification', :classification, 'source', :source,
                                           'previousVersion', :version), :occurredAt
                        FROM app_user u WHERE u.email = :email
                        """)
                .param("auditId", UUID.randomUUID().toString())
                .param("entityId", id.toString())
                .param("classification", classification)
                .param("source", source)
                .param("version", expectedVersion)
                .param("strategyVersion", strategies.current().version())
                .param("occurredAt", clock.instant())
                .param("email", email)
                .update();
        return position(email, id).orElseThrow();
    }

    static String bucketFor(String classification) {
        return switch (classification) {
            case "CORE_BROAD_ETF", "CORE_TECH_ETF", "CASH_EQUIVALENT" -> "CORE";
            default -> "TACTICAL_OVERLAY";
        };
    }

    public List<HoldingAnalysisView> analyses(String email) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(h.id) id, BIN_TO_UUID(h.position_id) position_id,
                               i.symbol, h.analysis_status, h.confidence, h.current_weight,
                               h.target_weight_min, h.target_weight_max, h.exact_quantity_allowed,
                               h.reasons, h.risks, h.change_conditions, h.rule_ids,
                               h.strategy_version, h.data_as_of, h.valid_until
                        FROM holding_analysis_snapshot h
                        JOIN position p ON p.id = h.position_id
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        JOIN instrument i ON i.id = p.instrument_id
                        WHERE u.email = :email
                        ORDER BY h.data_as_of DESC
                        """)
                .param("email", email)
                .query(HoldingAnalysisView.class)
                .list();
    }

    public List<RecommendationView> activeRecommendations(String email) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(r.id) id, BIN_TO_UUID(r.position_id) position_id,
                               i.symbol, COALESCE(JSON_UNQUOTE(JSON_EXTRACT(i.metadata,'$.name')),i.symbol) company_name,
                               p.classification,COALESCE(m.marked_market_value,0) market_value,
                               h.current_weight, r.action, r.priority, r.quantity_min, r.quantity_max,
                               r.target_weight_min, r.target_weight_max, r.risk_before_fraction,
                               r.risk_after_fraction, r.risk_calculation_reason, r.tax_lot_status, r.confidence, r.reasons, r.risks,
                               r.change_conditions, r.rule_ids, r.strategy_version,
                               r.data_as_of, r.valid_until,
                               CASE WHEN r.quantity_max IS NULL OR q.last_price IS NULL THEN NULL
                                    ELSE r.quantity_max*q.last_price END estimated_amount
                        FROM recommendation r
                        JOIN app_user u ON u.id = r.user_id
                        LEFT JOIN position p ON p.id = r.position_id
                        LEFT JOIN instrument i ON i.id = p.instrument_id
                        LEFT JOIN current_position_mark m ON m.position_id=p.id
                        LEFT JOIN holding_analysis_snapshot h ON h.id=r.holding_analysis_id
                        LEFT JOIN quote q ON q.id=(SELECT x.id FROM quote x WHERE x.instrument_id=p.instrument_id
                            ORDER BY x.data_as_of DESC,x.created_at DESC LIMIT 1)
                        WHERE u.email = :email AND r.status = 'ACTIVE' AND r.valid_until > UTC_TIMESTAMP(6)
                        ORDER BY FIELD(r.priority, 'MUST_ACT', 'DO_NOT', 'WATCH', 'NORMAL'), r.created_at
                        """)
                .param("email", email)
                .query(RecommendationView.class)
                .list();
    }

    public record AccountView(
            UUID id,
            String institution,
            String accountType,
            String displayName,
            String currency,
            boolean active,
            long version) {}

    public record PortfolioSummaryView(
            BigDecimal investedValue, BigDecimal trackedCash, long openPositions, LocalDateTime dataAsOf) {}

    public record PositionView(
            UUID id,
            UUID accountId,
            String symbol,
            String bucket,
            String classification,
            boolean classificationConfirmed,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal marketValue,
            String status,
            long version) {}

    public record ClassificationEvidence(
            String symbol, String assetType, boolean thematic, boolean unvestedCompensation) {}

    public record PortfolioHoldingView(
            UUID id,
            long version,
            String symbol,
            String name,
            String assetType,
            String bucket,
            String classification,
            boolean classificationConfirmed,
            BigDecimal marketValue,
            BigDecimal currentWeight,
            BigDecimal targetWeightMin,
            BigDecimal targetWeightMax,
            String action,
            String priority,
            String confidence,
            String trend,
            LocalDateTime nextEvent,
            String dataStatus) {}

    public record HoldingAnalysisView(
            UUID id,
            UUID positionId,
            String symbol,
            String analysisStatus,
            String confidence,
            BigDecimal currentWeight,
            BigDecimal targetWeightMin,
            BigDecimal targetWeightMax,
            boolean exactQuantityAllowed,
            String reasons,
            String risks,
            String changeConditions,
            String ruleIds,
            String strategyVersion,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil) {}

    public record RecommendationView(
            UUID id,
            UUID positionId,
            String symbol,
            String companyName,
            String classification,
            BigDecimal marketValue,
            BigDecimal currentWeight,
            String action,
            String priority,
            BigDecimal quantityMin,
            BigDecimal quantityMax,
            BigDecimal targetWeightMin,
            BigDecimal targetWeightMax,
            BigDecimal riskBeforeFraction,
            BigDecimal riskAfterFraction,
            String riskCalculationReason,
            String taxLotStatus,
            String confidence,
            String reasons,
            String risks,
            String changeConditions,
            String ruleIds,
            String strategyVersion,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil,
            BigDecimal estimatedAmount) {}
}
