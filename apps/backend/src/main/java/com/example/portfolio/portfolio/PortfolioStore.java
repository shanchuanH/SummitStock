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
public class PortfolioStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    public PortfolioStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
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
                            COALESCE((SELECT SUM(p.market_value)
                                      FROM position p
                                      JOIN investment_account a ON a.id = p.account_id
                                      JOIN app_user u ON u.id = a.user_id
                                      WHERE u.email = :email AND p.status = 'OPEN'), 0) invested_value,
                            COALESCE((SELECT SUM(c.current_amount)
                                      FROM cash_bucket c JOIN app_user u ON u.id = c.user_id
                                      WHERE u.email = :email), 0) tracked_cash,
                            COALESCE((SELECT COUNT(*)
                                      FROM position p
                                      JOIN investment_account a ON a.id = p.account_id
                                      JOIN app_user u ON u.id = a.user_id
                                      WHERE u.email = :email AND p.status = 'OPEN'), 0) open_positions,
                            (SELECT MAX(e.data_as_of)
                             FROM equity_snapshot e
                             JOIN investment_account a ON a.id = e.account_id
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
                               p.quantity, p.average_cost, p.market_value, p.status, p.version
                        FROM position p
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        JOIN instrument i ON i.id = p.instrument_id
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
                               p.quantity, p.average_cost, p.market_value, p.status, p.version
                        FROM position p
                        JOIN investment_account a ON a.id = p.account_id
                        JOIN app_user u ON u.id = a.user_id
                        JOIN instrument i ON i.id = p.instrument_id
                        WHERE u.email = :email AND p.id = UUID_TO_BIN(:id)
                        """)
                .param("email", email)
                .param("id", id.toString())
                .query(PositionView.class)
                .optional();
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
                            p.classification_confirmed = TRUE,
                            p.classification_source = :source,
                            p.updated_at = :updatedAt,
                            p.version = p.version + 1
                        WHERE p.id = UUID_TO_BIN(:id) AND u.email = :email AND p.version = :version
                        """)
                .param("classification", classification)
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
                               '1.0.0-draft', JSON_ARRAY('POSITION.CLASSIFY.001'),
                               JSON_OBJECT('classification', :classification, 'source', :source,
                                           'previousVersion', :version), :occurredAt
                        FROM app_user u WHERE u.email = :email
                        """)
                .param("auditId", UUID.randomUUID().toString())
                .param("entityId", id.toString())
                .param("classification", classification)
                .param("source", source)
                .param("version", expectedVersion)
                .param("occurredAt", clock.instant())
                .param("email", email)
                .update();
        return position(email, id).orElseThrow();
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
                               i.symbol, r.action, r.priority, r.quantity_min, r.quantity_max,
                               r.target_weight_min, r.target_weight_max, r.risk_before_fraction,
                               r.risk_after_fraction, r.confidence, r.reasons, r.risks,
                               r.change_conditions, r.rule_ids, r.strategy_version,
                               r.data_as_of, r.valid_until
                        FROM recommendation r
                        JOIN app_user u ON u.id = r.user_id
                        LEFT JOIN position p ON p.id = r.position_id
                        LEFT JOIN instrument i ON i.id = p.instrument_id
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
            String action,
            String priority,
            BigDecimal quantityMin,
            BigDecimal quantityMax,
            BigDecimal targetWeightMin,
            BigDecimal targetWeightMax,
            BigDecimal riskBeforeFraction,
            BigDecimal riskAfterFraction,
            String confidence,
            String reasons,
            String risks,
            String changeConditions,
            String ruleIds,
            String strategyVersion,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil) {}
}
