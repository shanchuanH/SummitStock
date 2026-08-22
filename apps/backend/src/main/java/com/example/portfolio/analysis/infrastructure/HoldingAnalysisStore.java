package com.example.portfolio.analysis.infrastructure;

import com.example.portfolio.analysis.application.HoldingAnalysisApplicationService.RiskProjection;
import com.example.portfolio.analysis.application.PositionSizing;
import com.example.portfolio.analysis.domain.HoldingAnalysisResult;
import com.example.portfolio.analysis.domain.RecommendationResolution;
import com.example.portfolio.analysis.narrative.NarrativeInput;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
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
    public UUID append(
            UUID analysisRunId,
            HoldingAnalysisResult value,
            RecommendationResolution resolution,
            NarrativeInput narrativeInput,
            RiskProjection riskProjection,
            PositionSizing.Result sizing,
            Instant createdAt) {
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT IGNORE INTO holding_analysis_snapshot (
                            id, analysis_run_id, position_id, strategy_version, analysis_status, readiness, confidence,
                            current_weight, target_weight_min, target_weight_max, exact_quantity_allowed,
                            recommended_action, recommended_quantity_min, recommended_quantity_max,
                            sizing_limiting_constraint, projected_position_weight, projected_total_risk,
                            projected_cluster_risk, sizing_risk_per_share, quantity_before_limiting_constraint,
                            reasons, risks, change_conditions, rule_ids, evidence_refs, evidence_checksum, config_hash,
                            decision_payload, data_as_of, valid_until, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:analysisRunId), UUID_TO_BIN(:positionId), :strategyVersion, :analysisStatus,
                            :readiness, :confidence, :currentWeight, :targetMin, :targetMax, :exactQuantity,
                            :action, :quantityMin, :quantityMax, :limitingConstraint, :projectedPositionWeight,
                            :projectedTotalRisk, :projectedClusterRisk, :riskPerShare, :quantityBeforeConstraint,
                            CAST(:reasons AS JSON), CAST(:risks AS JSON),
                            CAST(:conditions AS JSON), CAST(:rules AS JSON), CAST(:evidenceRefs AS JSON), :checksum, :configHash,
                            CAST(:decisionPayload AS JSON), :dataAsOf, :validUntil, :createdAt
                        )
                        """)
                .param("id", id.toString())
                .param("analysisRunId", analysisRunId == null ? null : analysisRunId.toString())
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
                .param("limitingConstraint", sizing.limitingConstraint())
                .param("projectedPositionWeight", sizing.projectedPositionWeight())
                .param("projectedTotalRisk", sizing.projectedTotalRisk())
                .param("projectedClusterRisk", sizing.projectedClusterRisk())
                .param("riskPerShare", sizing.riskPerShare())
                .param("quantityBeforeConstraint", sizing.quantityBeforeLimitingConstraint())
                .param("reasons", serialize(value.reasons()))
                .param("risks", serialize(value.risks()))
                .param("conditions", serialize(value.changeConditions()))
                .param("rules", serialize(value.ruleIds()))
                .param("evidenceRefs", serialize(value.evidenceRefs()))
                .param("checksum", value.evidenceChecksum())
                .param("configHash", value.configHash())
                .param(
                        "decisionPayload",
                        serialize(new PersistedDecision(value, resolution, narrativeInput, riskProjection)))
                .param("dataAsOf", value.dataAsOf())
                .param("validUntil", value.validUntil())
                .param("createdAt", createdAt)
                .update();
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) FROM holding_analysis_snapshot
                        WHERE id=UUID_TO_BIN(:id)
                        """)
                .param("id", id.toString())
                .query(UUID.class)
                .single();
    }

    public List<PersistedHolding> forRun(UUID userId, UUID analysisRunId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(h.id) snapshotId, CAST(h.decision_payload AS CHAR) decisionPayload
                        FROM holding_analysis_snapshot h
                        JOIN position p ON p.id=h.position_id
                        JOIN investment_account a ON a.id=p.account_id
                        WHERE h.analysis_run_id=UUID_TO_BIN(:runId)
                          AND a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        ORDER BY h.created_at, h.id
                        """)
                .param("runId", analysisRunId.toString())
                .param("userId", userId.toString())
                .query((rs, rowNum) -> {
                    var decision = deserialize(rs.getString("decisionPayload"), PersistedDecision.class);
                    return new PersistedHolding(
                            UUID.fromString(rs.getString("snapshotId")),
                            decision.analysis(),
                            decision.resolution(),
                            decision.narrativeInput(),
                            decision.riskProjection());
                })
                .list();
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
            RiskProjection riskProjection,
            Instant createdAt) {
        var id = UUID.randomUUID();
        var recommendationChecksum = checksumMaterial(analysis, resolution);
        var taxLotStatus = taxLotStatus(analysis, resolution);
        jdbc.sql(
                        """
                        INSERT INTO recommendation (
                            id, user_id, position_id, holding_analysis_id, strategy_version, action, priority,
                            quantity_min, quantity_max, target_weight_min, target_weight_max,
                            risk_before_fraction, risk_after_fraction, risk_calculation_reason, tax_lot_status, confidence, reasons, risks,
                            change_conditions, rule_ids, evidence_refs, evidence_checksum, data_as_of, valid_until, status,
                            winning_rule, suppressed_candidates, resolution_reason, config_hash, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), UUID_TO_BIN(:positionId), UUID_TO_BIN(:analysisId),
                            :strategyVersion, :action, :priority, :quantityMin, :quantityMax, :targetMin, :targetMax,
                            :riskBefore, :riskAfter, :riskReason, :taxLotStatus, :confidence, CAST(:reasons AS JSON), CAST(:risks AS JSON),
                            CAST(:conditions AS JSON), CAST(:rules AS JSON), CAST(:evidenceRefs AS JSON), :checksum, :dataAsOf, :validUntil,
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
                .param("riskBefore", riskProjection.beforeFraction())
                .param("riskAfter", riskProjection.afterFraction())
                .param("riskReason", riskProjection.reason())
                .param("taxLotStatus", taxLotStatus)
                .param("confidence", analysis.confidence())
                .param("reasons", serialize(analysis.reasons()))
                .param("risks", serialize(analysis.risks()))
                .param("conditions", serialize(analysis.changeConditions()))
                .param("rules", serialize(analysis.ruleIds()))
                .param("evidenceRefs", serialize(analysis.evidenceRefs()))
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

    private String taxLotStatus(HoldingAnalysisResult analysis, RecommendationResolution resolution) {
        if (!com.example.portfolio.analysis.decision.RecommendationSizingService.requiresSellSizing(
                resolution.winner().action())) return "NOT_APPLICABLE";
        var coveredQuantity = jdbc.sql(
                        """
                        SELECT COALESCE(SUM(t.quantity),0) FROM tax_lot t
                        JOIN position_lot l ON l.id=t.position_lot_id
                        WHERE l.position_id=UUID_TO_BIN(:positionId)
                        """)
                .param("positionId", analysis.positionId().toString())
                .query(BigDecimal.class)
                .single();
        return analysis.recommendedQuantityMax() != null
                        && coveredQuantity.compareTo(analysis.recommendedQuantityMax()) >= 0
                ? "TAX_LOTS_AVAILABLE_NOT_OPTIMIZED"
                : "TAX_DATA_MISSING";
    }

    public Optional<PositionReportRow> latestReport(UUID userId, UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) positionId, i.symbol, c.classification,
                               c.classification_source classificationSource,
                               h.analysis_status analysisStatus, h.readiness, h.confidence,
                               h.current_weight currentWeight, h.target_weight_min targetWeightMin,
                               h.target_weight_max targetWeightMax, h.exact_quantity_allowed exactQuantityAllowed,
                               h.recommended_action recommendedAction,
                               h.recommended_quantity_min recommendedQuantityMin,
                               h.recommended_quantity_max recommendedQuantityMax,
                               h.sizing_limiting_constraint sizingLimitingConstraint,
                               h.projected_position_weight projectedPositionWeight,
                               h.projected_total_risk projectedTotalRisk,
                               h.projected_cluster_risk projectedClusterRisk,
                               h.sizing_risk_per_share sizingRiskPerShare,
                               h.quantity_before_limiting_constraint quantityBeforeLimitingConstraint,
                               h.reasons, h.risks, h.change_conditions changeConditions, h.rule_ids ruleIds,
                               h.evidence_refs evidenceRefs,
                               h.strategy_version strategyVersion, h.config_hash configHash,
                               h.data_as_of dataAsOf, h.valid_until validUntil,
                               BIN_TO_UUID(h.analysis_run_id) analysisRunId, ar.market_date marketDate,
                               ar.data_as_of runDataAsOf, ar.decision_cutoff runDecisionCutoff,
                               ar.strategy_version runStrategyVersion,
                               BIN_TO_UUID(r.id) recommendationId, r.action recommendationAction,
                               r.priority recommendationPriority, r.winning_rule winningRule,
                               r.risk_before_fraction riskBeforeFraction,
                               r.risk_after_fraction riskAfterFraction,
                               r.risk_calculation_reason riskCalculationReason,
                               r.suppressed_candidates suppressedCandidates, r.resolution_reason resolutionReason,
                               n.source narrativeSource, n.headline narrativeHeadline,
                               n.one_sentence narrativeOneSentence, n.why_items narrativeWhy,
                               n.risk_items narrativeRisks, n.watch_next narrativeWatchNext,
                               n.confidence_explanation narrativeConfidenceExplanation
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN instrument i ON i.id=p.instrument_id
                        LEFT JOIN recommendation r ON r.id=(
                            SELECT y.id FROM recommendation y WHERE y.position_id=p.id AND y.user_id=a.user_id
                            ORDER BY (y.status='ACTIVE') DESC, y.data_as_of DESC, y.created_at DESC LIMIT 1)
                        LEFT JOIN holding_analysis_snapshot h ON h.id=r.holding_analysis_id
                        LEFT JOIN portfolio_analysis_run ar ON ar.id=h.analysis_run_id
                        LEFT JOIN analysis_run_position_classification rc
                          ON rc.analysis_run_id=h.analysis_run_id AND rc.position_id=p.id
                        LEFT JOIN position_classification_snapshot c ON c.id=rc.classification_snapshot_id
                        LEFT JOIN decision_narrative n ON n.recommendation_id=r.id
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

    private <T> T deserialize(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not deserialize persisted analysis decision", exception);
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
            String sizingLimitingConstraint,
            BigDecimal projectedPositionWeight,
            BigDecimal projectedTotalRisk,
            BigDecimal projectedClusterRisk,
            BigDecimal sizingRiskPerShare,
            BigDecimal quantityBeforeLimitingConstraint,
            String reasons,
            String risks,
            String changeConditions,
            String ruleIds,
            String evidenceRefs,
            String strategyVersion,
            String configHash,
            LocalDateTime dataAsOf,
            LocalDateTime validUntil,
            UUID analysisRunId,
            java.time.LocalDate marketDate,
            LocalDateTime runDataAsOf,
            LocalDateTime runDecisionCutoff,
            String runStrategyVersion,
            UUID recommendationId,
            String recommendationAction,
            String recommendationPriority,
            String winningRule,
            BigDecimal riskBeforeFraction,
            BigDecimal riskAfterFraction,
            String riskCalculationReason,
            String suppressedCandidates,
            String resolutionReason,
            String narrativeSource,
            String narrativeHeadline,
            String narrativeOneSentence,
            String narrativeWhy,
            String narrativeRisks,
            String narrativeWatchNext,
            String narrativeConfidenceExplanation) {}

    private record PersistedDecision(
            HoldingAnalysisResult analysis,
            RecommendationResolution resolution,
            NarrativeInput narrativeInput,
            RiskProjection riskProjection) {}

    public record PersistedHolding(
            UUID snapshotId,
            HoldingAnalysisResult analysis,
            RecommendationResolution resolution,
            NarrativeInput narrativeInput,
            RiskProjection riskProjection) {}
}
