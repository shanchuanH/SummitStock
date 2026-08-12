package com.example.portfolio.context;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BreadthService {
    private static final BigDecimal HEALTHY_COVERAGE = new BigDecimal("0.90");
    private final JdbcClient jdbc;
    private final Clock clock;

    public BreadthService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public CaptureResult capture(LocalDate marketDate) {
        int snapshots = 0;
        for (var universe : universes()) {
            var evidence = evidence(universe.id(), marketDate);
            var expected = BigDecimal.valueOf(universe.expectedMemberCount());
            var coverage = expected.signum() == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(evidence.covered200())
                            .divide(expected, MathContext.DECIMAL64)
                            .min(BigDecimal.ONE);
            var quality = coverage.compareTo(HEALTHY_COVERAGE) >= 0
                    ? "HEALTHY"
                    : evidence.covered200() > 0 ? "PARTIAL" : "MISSING";
            var dataAsOf = evidence.dataAsOf() == null
                    ? marketDate.atStartOfDay(clock.getZone()).toInstant()
                    : evidence.dataAsOf().toInstant(ZoneOffset.UTC);
            var canonical = universe.code() + "|" + marketDate + "|" + evidence + "|" + coverage + "|" + quality;
            snapshots += jdbc.sql(
                            """
                            INSERT IGNORE INTO breadth_snapshot (
                              id,universe_id,universe_code,market_date,pct_above_sma50,pct_above_sma200,
                              advance_decline,member_count,covered_member_count,coverage,quality,
                              evidence_checksum,data_as_of,created_at
                            ) VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:universeId),:code,:marketDate,:above50,:above200,
                              :advanceDecline,:members,:covered,:coverage,:quality,:checksum,:dataAsOf,:createdAt)
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("universeId", universe.id().toString())
                    .param("code", universe.code())
                    .param("marketDate", marketDate)
                    .param("above50", evidence.pctAbove50())
                    .param("above200", evidence.pctAbove200())
                    .param("advanceDecline", evidence.advanceDecline())
                    .param("members", evidence.memberCount())
                    .param("covered", evidence.covered200())
                    .param("coverage", coverage)
                    .param("quality", quality)
                    .param("checksum", sha256(canonical))
                    .param("dataAsOf", dataAsOf)
                    .param("createdAt", clock.instant())
                    .update();
        }
        return new CaptureResult(universes().size(), snapshots);
    }

    public CompositeBreadth latest(LocalDate marketDate) {
        var rows = jdbc.sql(
                        """
                        WITH ranked AS (
                          SELECT universe_code,pct_above_sma50,pct_above_sma200,coverage,quality,data_as_of,
                                 ROW_NUMBER() OVER (PARTITION BY universe_code ORDER BY market_date DESC,data_as_of DESC,created_at DESC) rn
                          FROM breadth_snapshot WHERE market_date<=:marketDate
                            AND universe_code IN ('SP500','NASDAQ100')
                        )
                        SELECT universe_code code,pct_above_sma50 pctAbove50,pct_above_sma200 pctAbove200,
                               coverage,quality,data_as_of dataAsOf FROM ranked WHERE rn=1
                        """)
                .param("marketDate", marketDate)
                .query(SnapshotRow.class)
                .list();
        if (rows.size() != 2 || rows.stream().anyMatch(row -> row.pctAbove50() == null)) {
            return new CompositeBreadth(null, null, "MISSING", rows.size(), latestDataAsOf(rows));
        }
        var above50 = average(rows.stream().map(SnapshotRow::pctAbove50).toList());
        var above200 = rows.stream().anyMatch(row -> row.pctAbove200() == null)
                ? null
                : average(rows.stream().map(SnapshotRow::pctAbove200).toList());
        var quality = rows.stream().allMatch(row -> "HEALTHY".equals(row.quality())) ? "HEALTHY" : "PARTIAL";
        return new CompositeBreadth(above50, above200, quality, rows.size(), latestDataAsOf(rows));
    }

    private List<UniverseRow> universes() {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) id,universe_code code,expected_member_count expectedMemberCount
                        FROM breadth_universe WHERE active=TRUE ORDER BY universe_code
                        """)
                .query(UniverseRow.class)
                .list();
    }

    private EvidenceRow evidence(UUID universeId, LocalDate marketDate) {
        return jdbc.sql(
                        """
                        WITH members AS (
                          SELECT instrument_id FROM breadth_universe_member
                          WHERE universe_id=UUID_TO_BIN(:universeId) AND valid_from<=:marketDate
                            AND (valid_to IS NULL OR valid_to>=:marketDate)
                        ), ranked AS (
                          SELECT p.instrument_id,p.close_price,p.data_as_of,
                                 ROW_NUMBER() OVER (PARTITION BY p.instrument_id ORDER BY p.market_date DESC,p.data_as_of DESC) rn
                          FROM price_bar p JOIN members m ON m.instrument_id=p.instrument_id
                          WHERE p.adjusted=TRUE AND p.market_date<=:marketDate
                        ), stats AS (
                          SELECT instrument_id,COUNT(*) observations,
                                 MAX(CASE WHEN rn=1 THEN close_price END) latest_close,
                                 MAX(CASE WHEN rn=2 THEN close_price END) prior_close,
                                 AVG(CASE WHEN rn<=50 THEN close_price END) sma50,
                                 AVG(CASE WHEN rn<=200 THEN close_price END) sma200,
                                 MAX(data_as_of) data_as_of
                          FROM ranked GROUP BY instrument_id
                        )
                        SELECT (SELECT COUNT(*) FROM members) memberCount,
                               COALESCE(SUM(observations>=200),0) covered200,
                               AVG(CASE WHEN observations>=50 THEN latest_close>sma50 END) pctAbove50,
                               AVG(CASE WHEN observations>=200 THEN latest_close>sma200 END) pctAbove200,
                               SUM(CASE WHEN prior_close IS NULL THEN 0 WHEN latest_close>prior_close THEN 1
                                        WHEN latest_close<prior_close THEN -1 ELSE 0 END) advanceDecline,
                               MAX(data_as_of) dataAsOf
                        FROM stats
                        """)
                .param("universeId", universeId.toString())
                .param("marketDate", marketDate)
                .query(EvidenceRow.class)
                .single();
    }

    private static BigDecimal average(List<BigDecimal> values) {
        return values.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), MathContext.DECIMAL64);
    }

    private static Instant latestDataAsOf(List<SnapshotRow> rows) {
        return rows.stream()
                .map(SnapshotRow::dataAsOf)
                .filter(java.util.Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .map(value -> value.toInstant(ZoneOffset.UTC))
                .orElse(null);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record CaptureResult(int universes, int snapshots) {}

    public record CompositeBreadth(
            BigDecimal pctAboveSma50, BigDecimal pctAboveSma200, String quality, int universeCount, Instant dataAsOf) {}

    record UniverseRow(UUID id, String code, int expectedMemberCount) {}

    record EvidenceRow(
            int memberCount,
            int covered200,
            BigDecimal pctAbove50,
            BigDecimal pctAbove200,
            BigDecimal advanceDecline,
            LocalDateTime dataAsOf) {}

    record SnapshotRow(
            String code,
            BigDecimal pctAbove50,
            BigDecimal pctAbove200,
            BigDecimal coverage,
            String quality,
            LocalDateTime dataAsOf) {}
}
