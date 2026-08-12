package com.example.portfolio.analysis.dip;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.analysis.capital.CapitalBaseService;
import com.example.portfolio.market.provider.TradingCalendar;
import com.example.portfolio.strategy.dip.EtfDipEngine;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EtfDipEventService {
    private static final Duration VALIDITY = Duration.ofHours(24);

    private final JdbcClient jdbc;
    private final PublishedStrategyService strategies;
    private final CapitalBaseService capitalBases;
    private final TradingCalendar calendar;
    private final Clock clock;

    public EtfDipEventService(
            JdbcClient jdbc,
            PublishedStrategyService strategies,
            CapitalBaseService capitalBases,
            TradingCalendar calendar,
            Clock clock) {
        this.jdbc = jdbc;
        this.strategies = strategies;
        this.capitalBases = capitalBases;
        this.calendar = calendar;
        this.clock = clock;
    }

    @Transactional
    public int evaluateAndCapture(UUID userId) {
        var capital = capitalBases.calculate(userId);
        var strategy = strategies.current();
        var reserve = tacticalReserve(userId);
        int affected = 0;
        for (var evidence : evidence(userId)) {
            var lastTranche = lastTranche(userId, evidence.instrumentId());
            int completed = lastTranche.map(LastTranche::completed).orElse(0);
            var lastDate = lastTranche.map(LastTranche::lastMarketDate).orElse(null);
            int sessionsSince = sessionsSince(lastDate, evidence.marketDate());
            var triggers = triggerCodes(evidence);
            boolean complete = evidence.instrumentDrawdown() != null
                    && evidence.portfolioDrawdown() != null
                    && evidence.dataAsOf() != null
                    && evidence.quality() != EvidenceQuality.MISSING;
            boolean emergencyProtected = capital.emergencyReserve().compareTo(strategy.emergencyCashFloor()) >= 0;
            var result = EtfDipEngine.evaluate(
                    new EtfDipEngine.Input(
                            zero(evidence.portfolioDrawdown()),
                            evidence.marketDriven(),
                            complete,
                            emergencyProtected,
                            drawdownScore(evidence.instrumentDrawdown()),
                            evidence.stressLevel(),
                            1 - evidence.breadth50(),
                            evidence.stressLevel(),
                            evidence.stressLevel(),
                            1 - Math.clamp(evidence.trendScore() / 40.0, 0, 1),
                            triggers.contains("RSI_CROSS_40"),
                            triggers.contains("BREAKOUT_5_DAY"),
                            triggers.contains("ABOVE_EMA_20"),
                            triggers.contains("BREADTH_IMPROVING"),
                            triggers.contains("VIX_FALLING"),
                            triggers.contains("CREDIT_STABLE"),
                            completed,
                            sessionsSince),
                    new EtfDipEngine.Policy(
                            strategy.etfDip().setupScoreMin(),
                            strategy.etfDip().requiredReversalSignals(),
                            strategy.etfDip().tranches(),
                            strategy.etfDip().cooldownTradingDays()));
            var state = state(result, completed);
            var cooldownUntil = lastDate == null
                    ? null
                    : plusSessions(lastDate, strategy.etfDip().cooldownTradingDays());
            var trancheAmount = result.reserveFraction() == null
                    ? BigDecimal.ZERO
                    : reserve.multiply(result.reserveFraction()).min(capital.deployableCash());
            var reserveAfter = result.reserveFraction() == null
                    ? reserve
                    : reserve.subtract(trancheAmount).max(BigDecimal.ZERO);
            var dataAsOf = evidence.dataAsOf() == null
                    ? clock.instant()
                    : evidence.dataAsOf().toInstant(ZoneOffset.UTC);
            var checksum = sha256(userId + ":" + evidence + ":" + result + ":" + strategy.configHash());
            affected += append(
                    userId,
                    evidence,
                    state,
                    result,
                    triggers,
                    lastDate,
                    cooldownUntil,
                    reserve,
                    reserveAfter,
                    emergencyProtected,
                    strategy.version(),
                    strategy.configHash(),
                    dataAsOf,
                    checksum);
        }
        return affected;
    }

    public Optional<EtfDipDecisionEvent> latest(UUID userId, UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT status state,setup_score setupScore,trigger_count triggerCount,
                               tranche_index trancheIndex,tranche_pct tranchePct,
                               cooldown_until_market_date cooldownUntilMarketDate,
                               reserve_before reserveBefore,reserve_after reserveAfter,quality,data_as_of dataAsOf
                        FROM etf_dip_event
                        WHERE user_id=UUID_TO_BIN(:userId) AND instrument_id=UUID_TO_BIN(:instrumentId)
                          AND valid_until>=:now
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("userId", userId.toString())
                .param("instrumentId", instrumentId.toString())
                .param("now", clock.instant())
                .query(EventRow.class)
                .optional()
                .map(row -> new EtfDipDecisionEvent(
                        row.state(),
                        row.setupScore(),
                        row.triggerCount(),
                        row.trancheIndex(),
                        row.tranchePct(),
                        row.cooldownUntilMarketDate(),
                        row.reserveBefore(),
                        row.reserveAfter(),
                        row.quality(),
                        row.dataAsOf().toInstant(ZoneOffset.UTC)));
    }

    private List<DipEvidence> evidence(UUID userId) {
        return jdbc.sql(
                        """
                        WITH latest_drawdown AS (
                          SELECT d.* FROM portfolio_drawdown_snapshot d
                          WHERE d.user_id=UUID_TO_BIN(:userId)
                          ORDER BY d.data_as_of DESC,d.created_at DESC LIMIT 1
                        ), latest_regime AS (
                          SELECT r.* FROM market_regime_snapshot r ORDER BY r.data_as_of DESC,r.created_at DESC LIMIT 1
                        )
                        SELECT BIN_TO_UUID(i.id) instrumentId,i.symbol,
                               GREATEST(1-(SELECT b.close_price FROM price_bar b WHERE b.instrument_id=i.id AND b.adjusted=TRUE
                                  ORDER BY b.market_date DESC LIMIT 1)/(SELECT MAX(h.close_price) FROM price_bar h
                                  WHERE h.instrument_id=i.id AND h.adjusted=TRUE),0) instrumentDrawdown,
                               d.drawdown_fraction portfolioDrawdown,d.market_driven marketDriven,
                               d.breadth50,d.stress_level stressLevel,r.trend_score trendScore,
                               CASE WHEN d.quality_status='HEALTHY' AND r.quality_status<>'MISSING' THEN 'HEALTHY'
                                    WHEN d.quality_status='MISSING' OR r.quality_status='MISSING' THEN 'MISSING'
                                    ELSE 'PARTIAL' END quality,
                               d.data_as_of dataAsOf,DATE(d.data_as_of) marketDate,
                               (SELECT s.value_double FROM indicator_snapshot s WHERE s.instrument_id=i.id
                                  AND s.indicator_code='RSI_14' ORDER BY s.market_date DESC LIMIT 1) latestRsi,
                               (SELECT s.value_double FROM indicator_snapshot s WHERE s.instrument_id=i.id
                                  AND s.indicator_code='RSI_14' ORDER BY s.market_date DESC LIMIT 1 OFFSET 1) priorRsi,
                               (SELECT b.close_price FROM price_bar b WHERE b.instrument_id=i.id AND b.adjusted=TRUE
                                  ORDER BY b.market_date DESC LIMIT 1) latestClose,
                               (SELECT s.value_double FROM indicator_snapshot s WHERE s.instrument_id=i.id
                                  AND s.indicator_code='EMA_20' ORDER BY s.market_date DESC LIMIT 1) ema20,
                               (SELECT MAX(x.close_price) FROM (SELECT b.close_price FROM price_bar b
                                  WHERE b.instrument_id=i.id AND b.adjusted=TRUE ORDER BY b.market_date DESC LIMIT 5 OFFSET 1) x)
                                  priorFiveHigh
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        JOIN instrument i ON i.id=p.instrument_id
                        JOIN latest_drawdown d JOIN latest_regime r
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                          AND p.classification IN ('CORE_BROAD_ETF','CORE_TECH_ETF')
                        GROUP BY i.id,i.symbol,d.id,r.id
                        """)
                .param("userId", userId.toString())
                .query(DipEvidence.class)
                .list();
    }

    private Optional<LastTranche> lastTranche(UUID userId, UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT COUNT(*) completed,MAX(t.planned_for) lastMarketDate
                        FROM etf_dip_tranche t JOIN etf_dip_event e ON e.id=t.dip_event_id
                        WHERE t.user_id=UUID_TO_BIN(:userId) AND e.instrument_id=UUID_TO_BIN(:instrumentId)
                          AND t.status='USER_CONFIRMED'
                        """)
                .param("userId", userId.toString())
                .param("instrumentId", instrumentId.toString())
                .query(LastTranche.class)
                .optional();
    }

    private BigDecimal tacticalReserve(UUID userId) {
        return jdbc.sql("SELECT COALESCE(SUM(current_amount),0) FROM cash_bucket "
                        + "WHERE user_id=UUID_TO_BIN(:userId) AND bucket_type='TACTICAL_RESERVE'")
                .param("userId", userId.toString())
                .query(BigDecimal.class)
                .single();
    }

    private int append(
            UUID userId,
            DipEvidence evidence,
            String state,
            EtfDipEngine.Result result,
            List<String> triggers,
            LocalDate lastDate,
            LocalDate cooldownUntil,
            BigDecimal reserveBefore,
            BigDecimal reserveAfter,
            boolean emergencyProtected,
            String strategyVersion,
            String configHash,
            Instant dataAsOf,
            String checksum) {
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO etf_dip_event (
                          id,user_id,instrument_id,strategy_version,config_hash,status,setup_score,trigger_count,
                          trigger_codes,tranche_index,tranche_pct,portfolio_drawdown,instrument_drawdown,
                          market_driven,emergency_cash_protected,last_tranche_market_date,cooldown_until_market_date,
                          reserve_before,reserve_after,quality,rule_ids,evidence_checksum,data_as_of,valid_until,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),UUID_TO_BIN(:instrumentId),:strategy,:configHash,
                          :status,:score,:triggerCount,CAST(:triggers AS JSON),:trancheIndex,:tranchePct,:portfolioDrawdown,
                          :instrumentDrawdown,:marketDriven,:emergencyProtected,:lastDate,:cooldownUntil,:reserveBefore,
                          :reserveAfter,:quality,CAST(:rules AS JSON),:checksum,:dataAsOf,:validUntil,:createdAt)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("instrumentId", evidence.instrumentId().toString())
                .param("strategy", strategyVersion)
                .param("configHash", configHash)
                .param("status", state)
                .param("score", result.setupScore())
                .param("triggerCount", result.triggerCount())
                .param("triggers", json(triggers))
                .param("trancheIndex", result.trancheNumber())
                .param("tranchePct", result.reserveFraction())
                .param("portfolioDrawdown", zero(evidence.portfolioDrawdown()))
                .param("instrumentDrawdown", evidence.instrumentDrawdown())
                .param("marketDriven", evidence.marketDriven())
                .param("emergencyProtected", emergencyProtected)
                .param("lastDate", lastDate)
                .param("cooldownUntil", cooldownUntil)
                .param("reserveBefore", reserveBefore)
                .param("reserveAfter", reserveAfter)
                .param("quality", evidence.quality().name())
                .param("rules", json(result.ruleIds()))
                .param("checksum", checksum)
                .param("dataAsOf", dataAsOf)
                .param("validUntil", dataAsOf.plus(VALIDITY))
                .param("createdAt", clock.instant())
                .update();
    }

    private static List<String> triggerCodes(DipEvidence value) {
        var result = new ArrayList<String>();
        if (value.latestRsi() != null && value.priorRsi() != null && value.latestRsi() >= 40 && value.priorRsi() < 40)
            result.add("RSI_CROSS_40");
        if (value.latestClose() != null
                && value.priorFiveHigh() != null
                && value.latestClose().compareTo(value.priorFiveHigh()) > 0) result.add("BREAKOUT_5_DAY");
        if (value.latestClose() != null
                && value.ema20() != null
                && value.latestClose().doubleValue() > value.ema20()) result.add("ABOVE_EMA_20");
        if (value.breadth50() >= 0.50) result.add("BREADTH_IMPROVING");
        if (value.stressLevel() < 0.50) result.add("CREDIT_STABLE");
        return List.copyOf(result);
    }

    private int sessionsSince(LocalDate from, LocalDate to) {
        return calendar.sessionsBetween(from, to);
    }

    private LocalDate plusSessions(LocalDate from, int sessions) {
        return calendar.plusSessions(from, sessions);
    }

    private static String state(EtfDipEngine.Result result, int completed) {
        return switch (result.action()) {
            case "DEPLOY_TRANCHE" -> "READY_FOR_TRANCHE_" + result.trancheNumber();
            case "COOLDOWN" -> "TRANCHE_" + completed + "_DEPLOYED";
            case "WAIT_FOR_SETUP", "WAIT_FOR_CONFIRMATION" -> "SETUP";
            case "HOLD_CORE_RECOVERY_TACTICAL_ONLY" -> "RECOVERY";
            case "PAIN_LINE_NO_NEW_RISK" -> "CLOSED";
            default -> "NORMAL";
        };
    }

    private static double drawdownScore(BigDecimal value) {
        return value == null ? 0 : Math.clamp(value.doubleValue() / 0.12, 0, 1);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String json(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record DipEvidence(
            UUID instrumentId,
            String symbol,
            BigDecimal instrumentDrawdown,
            BigDecimal portfolioDrawdown,
            boolean marketDriven,
            double breadth50,
            double stressLevel,
            double trendScore,
            EvidenceQuality quality,
            LocalDateTime dataAsOf,
            LocalDate marketDate,
            Double latestRsi,
            Double priorRsi,
            BigDecimal latestClose,
            Double ema20,
            BigDecimal priorFiveHigh) {}

    record LastTranche(int completed, LocalDate lastMarketDate) {}

    record EventRow(
            String state,
            double setupScore,
            int triggerCount,
            Integer trancheIndex,
            BigDecimal tranchePct,
            LocalDate cooldownUntilMarketDate,
            BigDecimal reserveBefore,
            BigDecimal reserveAfter,
            EvidenceQuality quality,
            LocalDateTime dataAsOf) {}
}
