package com.example.portfolio.analysis.mark;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.market.provider.TradingCalendar;
import java.math.BigDecimal;
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
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionMarkService {
    private final JdbcClient jdbc;
    private final PublishedStrategyService strategies;
    private final TradingCalendar calendar;
    private final Clock clock;

    public PositionMarkService(
            JdbcClient jdbc, PublishedStrategyService strategies, TradingCalendar calendar, Clock clock) {
        this.jdbc = jdbc;
        this.strategies = strategies;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional
    public CaptureResult captureForUser(UUID userId, Instant dataAsOf) {
        var completedSession = calendar.latestCompletedSession(dataAsOf);
        var strategy = strategies.current();
        int marked = 0;
        int stale = 0;
        int missing = 0;
        for (var row : markInputs(userId, completedSession)) {
            if (row.marketDate() == null || row.closePrice() == null) {
                missing++;
                continue;
            }
            var quality = row.marketDate().equals(completedSession) ? "HEALTHY" : "STALE";
            if (!"HEALTHY".equals(quality)) stale++;
            var marketValue = row.quantity().multiply(row.closePrice());
            var checksum = checksum(
                    row.positionId() + ":" + row.quantity() + ":" + row.barChecksum() + ":" + strategy.configHash());
            marked += jdbc.sql(
                            """
                            INSERT IGNORE INTO position_mark_snapshot (
                                id,position_id,instrument_id,quantity,decision_price,marked_market_value,
                                market_date,source_provider,quality_status,price_data_as_of,
                                strategy_version,strategy_config_hash,evidence_checksum,data_as_of,created_at
                            ) VALUES (
                                UUID_TO_BIN(:id),UUID_TO_BIN(:positionId),UUID_TO_BIN(:instrumentId),
                                :quantity,:price,:marketValue,:marketDate,:provider,:quality,:priceAsOf,
                                :strategyVersion,:configHash,:checksum,:dataAsOf,:createdAt
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("positionId", row.positionId().toString())
                    .param("instrumentId", row.instrumentId().toString())
                    .param("quantity", row.quantity())
                    .param("price", row.closePrice())
                    .param("marketValue", marketValue)
                    .param("marketDate", row.marketDate())
                    .param("provider", row.provider())
                    .param("quality", quality)
                    .param("priceAsOf", row.priceDataAsOf())
                    .param("strategyVersion", strategy.version())
                    .param("configHash", strategy.configHash())
                    .param("checksum", checksum)
                    .param("dataAsOf", dataAsOf)
                    .param("createdAt", clock.instant())
                    .update();
        }
        return new CaptureResult(marked, missing, stale, completedSession);
    }

    @Transactional
    public int captureAll(Instant dataAsOf) {
        return jdbc
                .sql(
                        """
                        SELECT DISTINCT BIN_TO_UUID(a.user_id)
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        WHERE p.status='OPEN' AND a.active=TRUE
                        """)
                .query(UUID.class)
                .list()
                .stream()
                .mapToInt(userId -> captureForUser(userId, dataAsOf).marked())
                .sum();
    }

    public Optional<PositionMark> latest(UUID positionId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) id,BIN_TO_UUID(position_id) positionId,
                               quantity,decision_price decisionPrice,marked_market_value marketValue,
                               market_date marketDate,source_provider sourceProvider,quality_status quality,
                               price_data_as_of priceDataAsOf,data_as_of dataAsOf
                        FROM current_position_mark WHERE position_id=UUID_TO_BIN(:positionId)
                        """)
                .param("positionId", positionId.toString())
                .query(PositionMarkRow.class)
                .optional()
                .map(row -> new PositionMark(
                        row.id(),
                        row.positionId(),
                        row.quantity(),
                        row.decisionPrice(),
                        row.marketValue(),
                        row.marketDate(),
                        row.sourceProvider(),
                        row.quality(),
                        instant(row.priceDataAsOf()),
                        instant(row.dataAsOf())));
    }

    private List<MarkInput> markInputs(UUID userId, LocalDate completedSession) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) positionId,BIN_TO_UUID(p.instrument_id) instrumentId,p.quantity,
                               b.close_price closePrice,b.market_date marketDate,b.provider,b.checksum barChecksum,
                               b.data_as_of priceDataAsOf
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        LEFT JOIN price_bar b ON b.id=(
                            SELECT x.id FROM price_bar x
                            WHERE x.instrument_id=p.instrument_id AND x.timeframe='1D' AND x.adjusted=TRUE
                              AND x.quality_status='HEALTHY' AND x.market_date<=:completedSession
                            ORDER BY x.market_date DESC,x.data_as_of DESC,x.created_at DESC LIMIT 1
                        )
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        ORDER BY p.id
                        """)
                .param("userId", userId.toString())
                .param("completedSession", completedSession)
                .query(MarkInput.class)
                .list();
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static String checksum(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record CaptureResult(int marked, int missing, int stale, LocalDate completedSession) {
        public boolean healthy() {
            return missing == 0 && stale == 0;
        }
    }

    public record PositionMark(
            UUID id,
            UUID positionId,
            BigDecimal quantity,
            BigDecimal decisionPrice,
            BigDecimal marketValue,
            LocalDate marketDate,
            String sourceProvider,
            String quality,
            Instant priceDataAsOf,
            Instant dataAsOf) {}

    record MarkInput(
            UUID positionId,
            UUID instrumentId,
            BigDecimal quantity,
            BigDecimal closePrice,
            LocalDate marketDate,
            String provider,
            String barChecksum,
            LocalDateTime priceDataAsOf) {}

    record PositionMarkRow(
            UUID id,
            UUID positionId,
            BigDecimal quantity,
            BigDecimal decisionPrice,
            BigDecimal marketValue,
            LocalDate marketDate,
            String sourceProvider,
            String quality,
            LocalDateTime priceDataAsOf,
            LocalDateTime dataAsOf) {}
}
