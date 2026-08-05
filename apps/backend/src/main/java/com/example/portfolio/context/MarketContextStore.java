package com.example.portfolio.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MarketContextStore {
    private final JdbcClient jdbc;

    public MarketContextStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int appendRegime(RegimeWrite value) {
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO market_regime_snapshot (
                            id, strategy_version, regime_label, total_score, trend_score,
                            momentum_score, breadth_score, stress_score, confidence,
                            tactical_cap_five_percent, quality_status, inputs_json, narratives,
                            rule_ids, evidence_checksum, data_as_of, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), :strategyVersion, :label, :total, :trend,
                            :momentum, :breadth, :stress, :confidence, :tacticalCap, :quality,
                            CAST(:inputs AS JSON), CAST(:narratives AS JSON), CAST(:rules AS JSON),
                            :checksum, :dataAsOf, :createdAt
                        )
                        """)
                .param("id", value.id().toString())
                .param("strategyVersion", value.strategyVersion())
                .param("label", value.label())
                .param("total", value.total())
                .param("trend", value.trend())
                .param("momentum", value.momentum())
                .param("breadth", value.breadth())
                .param("stress", value.stress())
                .param("confidence", value.confidence())
                .param("tacticalCap", value.tacticalCap())
                .param("quality", value.quality())
                .param("inputs", value.inputsJson())
                .param("narratives", value.narrativesJson())
                .param("rules", value.ruleIdsJson())
                .param("checksum", value.evidenceChecksum())
                .param("dataAsOf", value.dataAsOf())
                .param("createdAt", value.createdAt())
                .update();
    }

    @Transactional
    public int appendDrawdown(DrawdownWrite value) {
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO portfolio_drawdown_snapshot (
                            id, user_id, strategy_version, current_equity, high_water_mark, drawdown_fraction,
                            drawdown_state, source_classification, market_driven, spy_return_from_peak,
                            qqq_return_from_peak, breadth50, stress_level, position_attribution,
                            cluster_attribution, confidence, quality_status, narratives, rule_ids,
                            evidence_checksum, data_as_of, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), :strategyVersion, :currentEquity, :highWaterMark, :drawdown,
                            :state, :source, :marketDriven, :spy, :qqq, :breadth50, :stress,
                            CAST(:positions AS JSON), CAST(:clusters AS JSON), :confidence, :quality,
                            CAST(:narratives AS JSON), CAST(:rules AS JSON), :checksum, :dataAsOf, :createdAt
                        )
                        """)
                .param("id", value.id().toString())
                .param("userId", value.userId().toString())
                .param("strategyVersion", value.strategyVersion())
                .param("currentEquity", value.currentEquity())
                .param("highWaterMark", value.highWaterMark())
                .param("drawdown", value.drawdown())
                .param("state", value.state())
                .param("source", value.source())
                .param("marketDriven", value.marketDriven())
                .param("spy", value.spyReturnFromPeak())
                .param("qqq", value.qqqReturnFromPeak())
                .param("breadth50", value.breadth50())
                .param("stress", value.stressLevel())
                .param("positions", value.positionAttributionJson())
                .param("clusters", value.clusterAttributionJson())
                .param("confidence", value.confidence())
                .param("quality", value.quality())
                .param("narratives", value.narrativesJson())
                .param("rules", value.ruleIdsJson())
                .param("checksum", value.evidenceChecksum())
                .param("dataAsOf", value.dataAsOf())
                .param("createdAt", value.createdAt())
                .update();
    }

    public Optional<RegimeView> latestRegime() {
        return jdbc.sql(
                        """
                        SELECT strategy_version, regime_label, total_score, trend_score,
                               momentum_score, breadth_score, stress_score, confidence,
                               tactical_cap_five_percent, quality_status, narratives, rule_ids,
                               data_as_of
                        FROM market_regime_snapshot
                        ORDER BY data_as_of DESC, created_at DESC
                        LIMIT 1
                        """)
                .query(RegimeView.class)
                .optional();
    }

    public Optional<DrawdownView> latestDrawdown(String email) {
        return jdbc.sql(
                        """
                        SELECT strategy_version, current_equity, high_water_mark, drawdown_fraction,
                               drawdown_state, source_classification, market_driven,
                               spy_return_from_peak, qqq_return_from_peak, breadth50, stress_level,
                               position_attribution, cluster_attribution, confidence, quality_status,
                               narratives, rule_ids, data_as_of
                        FROM portfolio_drawdown_snapshot d
                        JOIN app_user u ON u.id=d.user_id
                        WHERE u.email=:email
                        ORDER BY d.data_as_of DESC, d.created_at DESC
                        LIMIT 1
                        """)
                .param("email", email)
                .query(DrawdownView.class)
                .optional();
    }

    public record RegimeWrite(
            UUID id,
            String strategyVersion,
            String label,
            double total,
            double trend,
            double momentum,
            double breadth,
            double stress,
            String confidence,
            boolean tacticalCap,
            String quality,
            String inputsJson,
            String narrativesJson,
            String ruleIdsJson,
            String evidenceChecksum,
            Instant dataAsOf,
            Instant createdAt) {}

    public record DrawdownWrite(
            UUID id,
            UUID userId,
            String strategyVersion,
            BigDecimal currentEquity,
            BigDecimal highWaterMark,
            BigDecimal drawdown,
            String state,
            String source,
            boolean marketDriven,
            BigDecimal spyReturnFromPeak,
            BigDecimal qqqReturnFromPeak,
            BigDecimal breadth50,
            BigDecimal stressLevel,
            String positionAttributionJson,
            String clusterAttributionJson,
            String confidence,
            String quality,
            String narrativesJson,
            String ruleIdsJson,
            String evidenceChecksum,
            Instant dataAsOf,
            Instant createdAt) {}

    public record RegimeView(
            String strategyVersion,
            String regimeLabel,
            double totalScore,
            double trendScore,
            double momentumScore,
            double breadthScore,
            double stressScore,
            String confidence,
            boolean tacticalCapFivePercent,
            String qualityStatus,
            String narratives,
            String ruleIds,
            LocalDateTime dataAsOf) {}

    public record DrawdownView(
            String strategyVersion,
            BigDecimal currentEquity,
            BigDecimal highWaterMark,
            BigDecimal drawdownFraction,
            String drawdownState,
            String sourceClassification,
            boolean marketDriven,
            BigDecimal spyReturnFromPeak,
            BigDecimal qqqReturnFromPeak,
            BigDecimal breadth50,
            BigDecimal stressLevel,
            String positionAttribution,
            String clusterAttribution,
            String confidence,
            String qualityStatus,
            String narratives,
            String ruleIds,
            LocalDateTime dataAsOf) {}
}
