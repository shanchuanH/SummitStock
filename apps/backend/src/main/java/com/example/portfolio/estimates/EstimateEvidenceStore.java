package com.example.portfolio.estimates;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EstimateEvidenceStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    public EstimateEvidenceStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<InstrumentRef> eligibleInstruments() {
        return jdbc.sql("SELECT BIN_TO_UUID(id) id, symbol FROM instrument WHERE active=TRUE AND asset_type='EQUITY'")
                .query(InstrumentRef.class)
                .list();
    }

    @Transactional
    public int save(UUID instrumentId, EarningsEstimateResult result) {
        int affected = 0;
        for (var estimate : result.estimates()) {
            var checksum = sha256(String.join(
                    "|",
                    instrumentId.toString(),
                    estimate.estimateType().name(),
                    estimate.periodEnd().toString(),
                    estimate.horizon(),
                    estimate.mean().toPlainString(),
                    result.dataAsOf().toString(),
                    result.source()));
            affected += jdbc.sql(
                            """
                            INSERT IGNORE INTO estimate_observation (
                                id, instrument_id, estimate_type, period_type, period_end, horizon,
                                mean_value, high_value, low_value, analyst_count, data_as_of,
                                source, quality, checksum, created_at
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), :type, :periodType, :periodEnd, :horizon,
                                :mean, :high, :low, :analysts, :dataAsOf, :source, :quality, :checksum, :now
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("instrumentId", instrumentId.toString())
                    .param("type", estimate.estimateType().name())
                    .param("periodType", estimate.periodType().name())
                    .param("periodEnd", estimate.periodEnd())
                    .param("horizon", estimate.horizon())
                    .param("mean", estimate.mean())
                    .param("high", estimate.high())
                    .param("low", estimate.low())
                    .param("analysts", estimate.analystCount())
                    .param("dataAsOf", result.dataAsOf())
                    .param("source", result.source())
                    .param("quality", result.quality().name())
                    .param("checksum", checksum)
                    .param("now", clock.instant())
                    .update();
        }
        return affected;
    }

    public List<EstimateRevisionEngine.Observation> observations(UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT estimate_type estimateType, period_type periodType, period_end periodEnd, horizon,
                               mean_value mean, high_value high, low_value low, analyst_count analystCount,
                               data_as_of dataAsOf
                        FROM estimate_observation WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                        ORDER BY period_end, data_as_of
                        """)
                .param("instrumentId", instrumentId.toString())
                .query((row, number) -> new EstimateRevisionEngine.Observation(
                        EarningsEstimateResult.EstimateType.valueOf(row.getString("estimateType")),
                        EarningsEstimateResult.PeriodType.valueOf(row.getString("periodType")),
                        row.getObject("periodEnd", LocalDate.class),
                        row.getString("horizon"),
                        row.getBigDecimal("mean"),
                        row.getBigDecimal("high"),
                        row.getBigDecimal("low"),
                        row.getObject("analystCount", Integer.class),
                        row.getObject("dataAsOf", LocalDateTime.class).toInstant(ZoneOffset.UTC)))
                .list();
    }

    @Transactional
    public int saveRevision(UUID instrumentId, EstimateRevisionEngine.RevisionResult result) {
        if (result.periodEnd() == null) return 0;
        var checksum = sha256(result.toString());
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO estimate_revision_snapshot (
                            id, instrument_id, period_end, horizon, revision_7d, revision_30d, revision_90d,
                            overall_revision, eps_change_7d, eps_change_30d, eps_change_90d,
                            revenue_change_7d, revenue_change_30d, revenue_change_90d, analyst_count,
                            dispersion, quality, evidence_checksum, data_as_of, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), :periodEnd, :horizon, :day7, :day30, :day90,
                            :overall, :eps7, :eps30, :eps90, :revenue7, :revenue30, :revenue90, :analysts,
                            :dispersion, :quality, :checksum, :dataAsOf, :now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("periodEnd", result.periodEnd())
                .param("horizon", result.horizon())
                .param("day7", result.day7().name())
                .param("day30", result.day30().name())
                .param("day90", result.day90().name())
                .param("overall", result.overall().name())
                .param("eps7", result.epsDay7())
                .param("eps30", result.epsDay30())
                .param("eps90", result.epsDay90())
                .param("revenue7", result.revenueDay7())
                .param("revenue30", result.revenueDay30())
                .param("revenue90", result.revenueDay90())
                .param("analysts", result.analystCount())
                .param("dispersion", result.dispersion())
                .param("quality", result.quality().name())
                .param("checksum", checksum)
                .param("dataAsOf", result.dataAsOf())
                .param("now", clock.instant())
                .update();
    }

    public record InstrumentRef(UUID id, String symbol) {}

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
