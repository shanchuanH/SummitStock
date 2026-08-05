package com.example.portfolio.analysis.infrastructure;

import com.example.portfolio.analysis.domain.HoldingAnalysisResult;
import com.example.portfolio.analysis.domain.RecommendationResolution;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class HoldingAnalysisStore {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public HoldingAnalysisStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public UUID append(HoldingAnalysisResult value, Instant createdAt) {
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT IGNORE INTO holding_analysis_snapshot (
                            id, position_id, strategy_version, analysis_status, readiness, confidence,
                            current_weight, target_weight_min, target_weight_max, exact_quantity_allowed,
                            recommended_action, recommended_quantity_min, recommended_quantity_max,
                            reasons, risks, change_conditions, rule_ids, evidence_checksum, config_hash,
                            data_as_of, valid_until, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:positionId), :strategyVersion, :analysisStatus,
                            :readiness, :confidence, :currentWeight, :targetMin, :targetMax, :exactQuantity,
                            :action, :quantityMin, :quantityMax, CAST(:reasons AS JSON), CAST(:risks AS JSON),
                            CAST(:conditions AS JSON), CAST(:rules AS JSON), :checksum, :configHash,
                            :dataAsOf, :validUntil, :createdAt
                        )
                        """)
                .param("id", id.toString())
                .param("positionId", value.positionId().toString())
                .param("strategyVersion", value.strategyVersion())
                .param("analysisStatus", value.analysisStatus())
                .param("readiness", value.readiness().name())
                .param("confidence", value.confidence())
                .param("currentWeight", value.currentWeight())
                .param("targetMin", value.targetWeightMin())
                .param("targetMax", value.targetWeightMax())
                .param("exactQuantity", value.exactQuantityAllowed())
                .param("action", value.recommendedAction().name())
                .param("quantityMin", value.recommendedQuantityMin())
                .param("quantityMax", value.recommendedQuantityMax())
                .param("reasons", serialize(value.reasons()))
                .param("risks", serialize(value.risks()))
                .param("conditions", serialize(value.changeConditions()))
                .param("rules", serialize(value.ruleIds()))
                .param("checksum", value.evidenceChecksum())
                .param("configHash", value.configHash())
                .param("dataAsOf", value.dataAsOf())
                .param("validUntil", value.validUntil())
                .param("createdAt", createdAt)
                .update();
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) FROM holding_analysis_snapshot
                        WHERE position_id=UUID_TO_BIN(:positionId) AND strategy_version=:strategyVersion
                          AND data_as_of=:dataAsOf AND evidence_checksum=:checksum
                        """)
                .param("positionId", value.positionId().toString())
                .param("strategyVersion", value.strategyVersion())
                .param("dataAsOf", value.dataAsOf())
                .param("checksum", value.evidenceChecksum())
                .query(UUID.class)
                .single();
    }

    @Transactional
    public void expireActive(UUID userId) {
        jdbc.sql(
                        """
                        UPDATE recommendation SET status='EXPIRED'
                        WHERE user_id=UUID_TO_BIN(:userId) AND status='ACTIVE'
                        """)
                .param("userId", userId.toString())
                .update();
    }

    @Transactional
    public UUID appendRecommendation(
            UUID userId,
            UUID analysisId,
            HoldingAnalysisResult analysis,
            RecommendationResolution resolution,
            Instant createdAt) {
        var id = UUID.randomUUID();
        var recommendationChecksum = checksumMaterial(analysis, resolution);
        jdbc.sql(
                        """
                        INSERT INTO recommendation (
                            id, user_id, position_id, holding_analysis_id, strategy_version, action, priority,
                            quantity_min, quantity_max, target_weight_min, target_weight_max,
                            risk_before_fraction, risk_after_fraction, confidence, reasons, risks,
                            change_conditions, rule_ids, evidence_checksum, data_as_of, valid_until, status,
                            winning_rule, suppressed_candidates, resolution_reason, config_hash, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), UUID_TO_BIN(:positionId), UUID_TO_BIN(:analysisId),
                            :strategyVersion, :action, :priority, :quantityMin, :quantityMax, :targetMin, :targetMax,
                            NULL, NULL, :confidence, CAST(:reasons AS JSON), CAST(:risks AS JSON),
                            CAST(:conditions AS JSON), CAST(:rules AS JSON), :checksum, :dataAsOf, :validUntil,
                            'ACTIVE', :winningRule, CAST(:suppressed AS JSON), :resolutionReason, :configHash, :createdAt
                        ) ON DUPLICATE KEY UPDATE
                            status='ACTIVE', valid_until=VALUES(valid_until), holding_analysis_id=VALUES(holding_analysis_id)
                        """)
                .param("id", id.toString())
                .param("userId", userId.toString())
                .param("positionId", analysis.positionId().toString())
                .param("analysisId", analysisId.toString())
                .param("strategyVersion", analysis.strategyVersion())
                .param("action", resolution.winner().action().name())
                .param("priority", resolution.winner().priority())
                .param("quantityMin", analysis.recommendedQuantityMin())
                .param("quantityMax", analysis.recommendedQuantityMax())
                .param("targetMin", analysis.targetWeightMin())
                .param("targetMax", analysis.targetWeightMax())
                .param("confidence", analysis.confidence())
                .param("reasons", serialize(analysis.reasons()))
                .param("risks", serialize(analysis.risks()))
                .param("conditions", serialize(analysis.changeConditions()))
                .param("rules", serialize(analysis.ruleIds()))
                .param("checksum", recommendationChecksum)
                .param("dataAsOf", analysis.dataAsOf())
                .param("validUntil", analysis.validUntil())
                .param("winningRule", resolution.winner().ruleId())
                .param("suppressed", serialize(resolution.suppressed()))
                .param("resolutionReason", resolution.reason())
                .param("configHash", analysis.configHash())
                .param("createdAt", createdAt)
                .update();
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) FROM recommendation
                        WHERE user_id=UUID_TO_BIN(:userId) AND strategy_version=:strategyVersion
                          AND evidence_checksum=:checksum
                        """)
                .param("userId", userId.toString())
                .param("strategyVersion", analysis.strategyVersion())
                .param("checksum", recommendationChecksum)
                .query(UUID.class)
                .single();
    }

    public Optional<PositionReportRow> latestReport(UUID userId, UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) positionId, i.symbol, p.classification,
                               p.classification_source classificationSource,
                               h.analysis_status analysisStatus, h.readiness, h.confidence,
                               h.current_weight currentWeight, h.target_weight_min targetWeightMin,
                               h.target_weight_max targetWeightMax, h.exact_quantity_allowed exactQuantityAllowed,
                               h.recommended_action recommendedAction,
                               h.recommended_quantity_min recommendedQuantityMin,
                               h.recommended_quantity_max recommendedQuantityMax,
                               h.reasons, h.risks, h.change_conditions changeConditions, h.rule_ids ruleIds,
                               h.strategy_version strategyVersion, h.config_hash configHash,
                               h.data_as_of dataAsOf, h.valid_until validUntil,
                               BIN_TO_UUID(r.id) recommendationId, r.action recommendationAction,
                               r.priority recommendationPriority, r.winning_rule winningRule,
                               r.suppressed_candidates suppressedCandidates, r.resolution_reason resolutionReason
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN instrument i ON i.id=p.instrument_id
                        LEFT JOIN holding_analysis_snapshot h ON h.id=(
                            SELECT x.id FROM holding_analysis_snapshot x WHERE x.position_id=p.id
                            ORDER BY x.data_as_of DESC, x.created_at DESC LIMIT 1)
                        LEFT JOIN recommendation r ON r.id=(
                            SELECT y.id FROM recommendation y WHERE y.position_id=p.id AND y.user_id=a.user_id
                            ORDER BY (y.status='ACTIVE') DESC, y.data_as_of DESC, y.created_at DESC LIMIT 1)
                        WHERE p.id=UUID_TO_BIN(:positionId) AND a.user_id=UUID_TO_BIN(:userId)
                        """)
                .param("positionId", positionId.toString())
                .param("userId", userId.toString())
                .query(PositionReportRow.class)
                .optional();
    }

    public UUID userId(String email) {
        return jdbc.sql("SELECT BIN_TO_UUID(id) FROM app_user WHERE email=:email AND status='ACTIVE'")
                .param("email", email)
                .query(UUID.class)
                .optional()
                .orElseThrow(() -> new IllegalArgumentException("Enabled user was not found"));
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize analysis evidence", exception);
        }
    }

    private String checksumMaterial(HoldingAnalysisResult analysis, RecommendationResolution resolution) {
        return com.example.portfolio.analysis.application.AnalysisChecksum.sha256(analysis.evidenceChecksum() + ":"
                + resolution.winner().ruleId() + ":" + serialize(resolution.suppressed()));
    }

    public record PositionReportRow(
            UUID positionId,
            String symbol,
            String classification,
            String classificationSource,
            String analysisStatus,
            String readiness,
            String confidence,
            BigDecimal currentWeight,
            BigDecimal targetWeightMin,
            BigDecimal targetWeightMax,
            boolean exactQuantityAllowed,
            String recommendedAction,
            BigDecimal recommendedQuantityMin,
            BigDecimal recommendedQuantityMax,
            String reasons,
            String risks,
            String changeConditions,
            String ruleIds,
            String strategyVersion,
            String configHash,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil,
            UUID recommendationId,
            String recommendationAction,
            String recommendationPriority,
            String winningRule,
            String suppressedCandidates,
            String resolutionReason) {}
}
