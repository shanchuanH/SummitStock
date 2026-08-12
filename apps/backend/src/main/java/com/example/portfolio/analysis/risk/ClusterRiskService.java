package com.example.portfolio.analysis.risk;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.analysis.capital.CapitalBaseService;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClusterRiskService {
    private final JdbcClient jdbc;
    private final CapitalBaseService capitalBases;
    private final PublishedStrategyService strategies;
    private final Clock clock;

    public ClusterRiskService(
            JdbcClient jdbc, CapitalBaseService capitalBases, PublishedStrategyService strategies, Clock clock) {
        this.jdbc = jdbc;
        this.capitalBases = capitalBases;
        this.strategies = strategies;
        this.clock = clock;
    }

    @Transactional
    public int capture(UUID userId, Instant dataAsOf) {
        var investable = capitalBases.calculate(userId).investableAssets();
        var strategy = strategies.current();
        int affected = 0;
        for (var value : calculate(userId, investable)) {
            var fraction = investable.signum() == 0
                    ? BigDecimal.ZERO
                    : value.openRiskAmount().divide(investable, MathContext.DECIMAL64);
            var quality = value.memberCount() == 0
                    ? EvidenceQuality.MISSING
                    : value.impairedCount() == 0 ? EvidenceQuality.HEALTHY : EvidenceQuality.PARTIAL;
            var checksum = sha256(value + ":" + investable + ":" + strategy.configHash());
            affected += jdbc.sql(
                            """
                            INSERT IGNORE INTO risk_cluster_snapshot (
                              id,risk_cluster_id,user_id,open_risk_amount,open_risk_fraction,member_count,quality,
                              data_as_of,strategy_version,strategy_config_hash,evidence_checksum,created_at)
                            VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:clusterId),UUID_TO_BIN(:userId),:amount,:fraction,
                              :members,:quality,:dataAsOf,:strategy,:configHash,:checksum,:createdAt)
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("clusterId", value.clusterId().toString())
                    .param("userId", userId.toString())
                    .param("amount", value.openRiskAmount())
                    .param("fraction", fraction)
                    .param("members", value.memberCount())
                    .param("quality", quality.name())
                    .param("dataAsOf", dataAsOf)
                    .param("strategy", strategy.version())
                    .param("configHash", strategy.configHash())
                    .param("checksum", checksum)
                    .param("createdAt", clock.instant())
                    .update();
        }
        return affected;
    }

    public ClusterRisk forPosition(UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(s.risk_cluster_id) clusterId,s.open_risk_amount openRiskAmount,
                               s.open_risk_fraction openRiskFraction,s.member_count memberCount,s.quality,s.data_as_of dataAsOf
                        FROM risk_cluster_membership m JOIN risk_cluster_snapshot s ON s.risk_cluster_id=m.risk_cluster_id
                        WHERE m.position_id=UUID_TO_BIN(:positionId)
                          AND s.data_as_of=(SELECT MAX(x.data_as_of) FROM risk_cluster_snapshot x
                                           WHERE x.risk_cluster_id=s.risk_cluster_id)
                        ORDER BY s.open_risk_fraction DESC,s.created_at DESC LIMIT 1
                        """)
                .param("positionId", positionId.toString())
                .query(ClusterRiskRow.class)
                .optional()
                .map(row -> new ClusterRisk(
                        row.clusterId(),
                        row.openRiskAmount(),
                        row.openRiskFraction(),
                        row.memberCount(),
                        row.quality(),
                        row.dataAsOf().toInstant(ZoneOffset.UTC)))
                .orElseGet(ClusterRisk::none);
    }

    private List<CalculatedCluster> calculate(UUID userId, BigDecimal investable) {
        return jdbc.sql(
                        """
                        WITH latest_risk AS (
                          SELECT r.*,ROW_NUMBER() OVER (PARTITION BY r.position_id ORDER BY r.data_as_of DESC,r.created_at DESC) rn
                          FROM position_risk_snapshot r
                        )
                        SELECT BIN_TO_UUID(c.id) clusterId,COUNT(m.position_id) memberCount,
                               COALESCE(SUM(CASE WHEN p.status='OPEN' THEN r.risk_amount ELSE 0 END),0) openRiskAmount,
                               COALESCE(SUM(CASE WHEN p.status='OPEN' AND (r.id IS NULL OR r.quality_status<>'HEALTHY')
                                                 THEN 1 ELSE 0 END),0) impairedCount
                        FROM risk_cluster c LEFT JOIN risk_cluster_membership m ON m.risk_cluster_id=c.id
                        LEFT JOIN position p ON p.id=m.position_id
                        LEFT JOIN latest_risk r ON r.position_id=p.id AND r.rn=1
                        WHERE c.user_id=UUID_TO_BIN(:userId)
                        GROUP BY c.id
                        """)
                .param("userId", userId.toString())
                .query(CalculatedCluster.class)
                .list();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record CalculatedCluster(UUID clusterId, int memberCount, BigDecimal openRiskAmount, int impairedCount) {}

    record ClusterRiskRow(
            UUID clusterId,
            BigDecimal openRiskAmount,
            BigDecimal openRiskFraction,
            int memberCount,
            EvidenceQuality quality,
            LocalDateTime dataAsOf) {}
}
