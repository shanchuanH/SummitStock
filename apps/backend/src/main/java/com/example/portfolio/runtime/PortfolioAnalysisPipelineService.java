package com.example.portfolio.runtime;

import com.example.portfolio.analysis.allocation.PortfolioAllocationService;
import com.example.portfolio.analysis.capital.CapitalBaseService;
import com.example.portfolio.analysis.dip.EtfDipEventService;
import com.example.portfolio.analysis.mark.PositionMarkService;
import com.example.portfolio.analysis.risk.ClusterRiskService;
import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.context.BreadthService;
import com.example.portfolio.context.MarketContextService;
import com.example.portfolio.macro.MacroApplicationService;
import com.example.portfolio.quant.Indicators;
import com.example.portfolio.quant.QuantBar;
import com.example.portfolio.strategy.market.DrawdownEngine;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.market.MarketRegimeEngine;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.StopEngine;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PortfolioAnalysisPipelineService {
    private final JdbcClient jdbc;
    private final MarketContextService contextService;
    private final PortfolioProperties properties;
    private final CapitalBaseService capitalBases;
    private final PositionMarkService positionMarks;
    private final PortfolioAllocationService allocations;
    private final EtfDipEventService dipEvents;
    private final ClusterRiskService clusterRisks;
    private final MacroApplicationService macro;
    private final BreadthService breadthService;
    private final Clock clock;

    public PortfolioAnalysisPipelineService(
            JdbcClient jdbc,
            MarketContextService contextService,
            PortfolioProperties properties,
            CapitalBaseService capitalBases,
            PositionMarkService positionMarks,
            PortfolioAllocationService allocations,
            EtfDipEventService dipEvents,
            ClusterRiskService clusterRisks,
            MacroApplicationService macro,
            BreadthService breadthService,
            Clock clock) {
        this.jdbc = jdbc;
        this.contextService = contextService;
        this.properties = properties;
        this.capitalBases = capitalBases;
        this.positionMarks = positionMarks;
        this.allocations = allocations;
        this.dipEvents = dipEvents;
        this.clusterRisks = clusterRisks;
        this.macro = macro;
        this.breadthService = breadthService;
        this.clock = clock;
    }

    public int collectBreadthMacro(LocalDate marketDate) {
        return breadthService.capture(marketDate).snapshots();
    }

    public int computeRegime(LocalDate marketDate) {
        var spy = benchmark("SPY", marketDate);
        var qqq = benchmark("QQQ", marketDate);
        var canonicalBreadth = breadthService.latest(marketDate);
        var breadth = canonicalBreadth.pctAboveSma50() == null
                ? 0
                : canonicalBreadth.pctAboveSma50().doubleValue();
        var quality = spy.available() && qqq.available() && !"MISSING".equals(canonicalBreadth.quality())
                ? "HEALTHY".equals(canonicalBreadth.quality()) ? EvidenceQuality.HEALTHY : EvidenceQuality.PARTIAL
                : EvidenceQuality.MISSING;
        double trend = (score(spy.above200()) + score(qqq.above200())) / 2.0;
        double momentum = qqq.rsi() == null ? 0 : Math.clamp(qqq.rsi() / 100.0, 0, 1);
        var realizedStress = qqq.realizedVolatility() == null
                ? null
                : BigDecimal.valueOf(Math.clamp(qqq.realizedVolatility() / 0.50, 0, 1));
        macro.computeFactors(marketDate, realizedStress);
        var macroFactors = macro.latestFactors(marketDate);
        double stressResilience = macroFactors.stressResilience() == null
                ? realizedStress == null ? 0 : 1 - realizedStress.doubleValue()
                : macroFactors.stressResilience().doubleValue();
        var vix = macro.latestValue("VIXCLS", marketDate);
        var input = new MarketRegimeEngine.Input(
                trend,
                momentum,
                breadth,
                stressResilience,
                spy.available() && !spy.above200(),
                qqq.available() && !qqq.above200(),
                vix == null ? 0 : vix.doubleValue(),
                breadth,
                qqq.macd() != null && qqq.macd() < 0,
                qqq.rsi() == null ? 0 : qqq.rsi(),
                false,
                quality);
        return contextService
                        .calculateRegime(
                                input, marketDate.atStartOfDay(clock.getZone()).toInstant())
                        .inserted()
                ? 1
                : 0;
    }

    public int syncPortfolio(UUID userId) {
        var updated = jdbc.sql(
                        """
                        UPDATE position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN (
                            SELECT s.position_id, s.quantity, s.average_cost, s.market_value, s.data_as_of,
                                   ROW_NUMBER() OVER (PARTITION BY s.position_id ORDER BY s.data_as_of DESC, s.created_at DESC) rn
                            FROM position_snapshot s
                        ) latest ON latest.position_id=p.id AND latest.rn=1
                        SET p.quantity=latest.quantity, p.average_cost=latest.average_cost,
                            p.market_value=latest.market_value, p.data_as_of=latest.data_as_of,
                            p.updated_at=:now, p.version=p.version+1
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                          AND (p.quantity<>latest.quantity OR p.market_value<>latest.market_value
                               OR NOT (p.average_cost <=> latest.average_cost))
                        """)
                .param("now", clock.instant())
                .param("userId", userId.toString())
                .update();
        return updated;
    }

    public PositionMarkService.CaptureResult capturePositionMarks(UUID userId) {
        var result = positionMarks.captureForUser(userId, clock.instant());
        capitalBases.capture(userId, clock.instant());
        allocations.capture(userId, clock.instant());
        return result;
    }

    public int computeDrawdown(UUID userId, LocalDate marketDate) {
        var totals = jdbc.sql(
                        """
                        SELECT COALESCE(SUM(m.marked_market_value),0) invested,
                               COALESCE((SELECT SUM(c.current_amount) FROM cash_bucket c
                                         WHERE c.user_id=UUID_TO_BIN(:userId)),0) cash,
                               COALESCE(MAX(m.marked_market_value),0) largest,
                               COUNT(p.id) openPositions,COUNT(m.id) markCount
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        LEFT JOIN current_position_mark m ON m.position_id=p.id
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        """)
                .param("userId", userId.toString())
                .query(PortfolioTotals.class)
                .single();
        if (totals.markCount() < totals.openPositions()
                || capitalBases.calculate(userId).quality() != EvidenceQuality.HEALTHY) {
            return 0;
        }
        var equity = totals.invested().add(totals.cash());
        if (equity.signum() <= 0) throw new PermanentDataException("NO_PORTFOLIO_EQUITY", "Portfolio has no equity");
        var priorHigh = jdbc.sql(
                        "SELECT MAX(high_water_mark) FROM portfolio_drawdown_snapshot WHERE user_id=UUID_TO_BIN(:userId)")
                .param("userId", userId.toString())
                .query(BigDecimal.class)
                .optional()
                .orElse(equity);
        var input = new DrawdownEngine.Input(
                equity,
                priorHigh,
                returnFromPeak("SPY", marketDate),
                returnFromPeak("QQQ", marketDate),
                canonicalBreadth50(marketDate),
                stressLevel(marketDate),
                totals.largest().divide(equity, MathContext.DECIMAL64).doubleValue(),
                largestClusterContribution(userId, equity),
                EvidenceQuality.PARTIAL);
        return contextService
                        .calculateDrawdown(userId, input, "{}", "{}", clock.instant())
                        .inserted()
                ? 1
                : 0;
    }

    public int recalculateStops(UUID userId, LocalDate marketDate) {
        int affected = 0;
        for (var row : jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) position_id,BIN_TO_UUID(i.id) instrument_id,p.classification,
                               COALESCE(p.average_cost,b.close_price) entry_price,b.close_price,
                               atr.value_double atr,ema.value_double ema20,high63.value_double rolling_high,
                               (SELECT MAX(s.live_stop) FROM stop_snapshot s WHERE s.position_id=p.id) previous_live_stop
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN instrument i ON i.id=p.instrument_id
                        JOIN price_bar b ON b.instrument_id=i.id AND b.adjusted=TRUE
                          AND b.market_date=(SELECT MAX(x.market_date) FROM price_bar x
                                             WHERE x.instrument_id=i.id AND x.adjusted=TRUE AND x.market_date<=:marketDate)
                        JOIN indicator_snapshot atr ON atr.instrument_id=i.id AND atr.indicator_code='ATR_14'
                          AND atr.market_date=b.market_date AND atr.status='READY'
                        JOIN indicator_snapshot ema ON ema.instrument_id=i.id AND ema.indicator_code='EMA_20'
                          AND ema.market_date=b.market_date AND ema.status='READY'
                        JOIN indicator_snapshot high63 ON high63.instrument_id=i.id AND high63.indicator_code='ROLLING_HIGH_63'
                          AND high63.market_date=b.market_date AND high63.status='READY'
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                          AND p.classification<>'CASH_EQUIVALENT'
                        """)
                .param("marketDate", marketDate)
                .param("userId", userId.toString())
                .query(StopInput.class)
                .list()) {
            var atr = BigDecimal.valueOf(row.atr());
            var swing = confirmedSwingLow(row.instrumentId(), marketDate);
            if (swing == null) continue;
            var result = StopEngine.calculate(new StopEngine.Input(
                    HoldingClassification.valueOf(row.classification()),
                    row.entryPrice(),
                    swing,
                    atr,
                    row.previousLiveStop(),
                    null,
                    BigDecimal.valueOf(row.ema20()),
                    swing,
                    row.closePrice(),
                    BigDecimal.valueOf(row.rollingHigh()),
                    null));
            if (!result.ordinaryStopApplicable()) continue;
            var checksum = sha256(row + ":" + marketDate);
            affected += jdbc.sql(
                            """
                            INSERT IGNORE INTO stop_snapshot (
                                id, position_id, strategy_version, entry_price, atr, structure_stop,
                                volatility_stop, initial_stop, live_stop, soft_alert, catastrophic_stop,
                                close_confirmed, rule_ids, quality_status, evidence_checksum, data_as_of, created_at
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:positionId), :strategy, :entry, :atr, :structure,
                                :volatility, :initial, :live, :soft, :catastrophic, :closeConfirmed,
                                :rules, 'HEALTHY', :checksum, :now, :now
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("positionId", row.positionId().toString())
                    .param("strategy", properties.strategyVersion())
                    .param("entry", row.entryPrice())
                    .param("atr", atr)
                    .param("structure", result.structureStop())
                    .param("volatility", result.volatilityStop())
                    .param("initial", result.initialStop())
                    .param("live", result.liveStop())
                    .param("soft", result.softAlert())
                    .param("catastrophic", result.catastrophicStop())
                    .param("closeConfirmed", result.closeConfirmed())
                    .param("rules", json(result.ruleIds()))
                    .param("checksum", checksum)
                    .param("now", clock.instant())
                    .update();
        }
        var positionRisks = snapshotPortfolioRisk(userId);
        var clusterRiskSnapshots = clusterRisks.capture(userId, clock.instant());
        return affected + positionRisks + clusterRiskSnapshots;
    }

    private int snapshotPortfolioRisk(UUID userId) {
        var investable = capitalBases.calculate(userId).investableAssets();
        return jdbc.sql(
                        """
                        INSERT INTO position_risk_snapshot (
                            id,position_id,strategy_version,current_weight,open_risk_fraction,
                            cluster_risk_fraction,risk_amount,quality_status,evidence_checksum,data_as_of,created_at)
                        WITH evidence AS (
                            SELECT p.id,p.quantity,p.average_cost,m.marked_market_value market_value,
                                   i.asset_type,p.classification,m.decision_price last_price,m.quality_status mark_quality,
                                   (SELECT s.live_stop FROM stop_snapshot s WHERE s.position_id=p.id
                                    ORDER BY s.data_as_of DESC,s.created_at DESC LIMIT 1) live_stop
                            FROM position p JOIN investment_account a ON a.id=p.account_id
                            JOIN instrument i ON i.id=p.instrument_id
                            LEFT JOIN current_position_mark m ON m.position_id=p.id
                            WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        ), calculated AS (
                            SELECT e.*,:investable equity,
                                   CASE WHEN e.classification NOT IN ('CORE_BROAD_ETF','CORE_TECH_ETF','THEMATIC_ETF','CASH_EQUIVALENT')
                                                  AND e.last_price IS NOT NULL AND e.live_stop IS NOT NULL
                                        THEN GREATEST((e.last_price-e.live_stop)*e.quantity,0)
                                        ELSE 0 END risk_amount
                            FROM evidence e
                        )
                        SELECT UUID_TO_BIN(UUID()),c.id,:strategy,
                               CASE WHEN c.equity=0 OR c.market_value IS NULL THEN 0 ELSE c.market_value/c.equity END,
                               CASE WHEN c.equity=0 THEN 0 ELSE c.risk_amount/c.equity END,
                               0,
                               c.risk_amount,
                               CASE WHEN c.last_price IS NULL OR c.mark_quality<>'HEALTHY'
                                      OR (c.classification NOT IN ('CORE_BROAD_ETF','CORE_TECH_ETF','THEMATIC_ETF','CASH_EQUIVALENT') AND c.live_stop IS NULL)
                                    THEN 'MISSING' ELSE 'HEALTHY' END,
                               SHA2(CONCAT(BIN_TO_UUID(c.id),':',COALESCE(c.market_value,''),':',COALESCE(c.last_price,''),':',COALESCE(c.live_stop,''),':',:now),256),
                               :now,:now
                        FROM calculated c WHERE c.equity>0
                        """)
                .param("userId", userId.toString())
                .param("investable", investable)
                .param("strategy", properties.strategyVersion())
                .param("now", clock.instant())
                .update();
    }

    public int updateThesesEvents(UUID userId) {
        return jdbc.sql(
                        """
                        SELECT COUNT(*) FROM position_thesis t
                        JOIN position p ON p.id=t.position_id JOIN investment_account a ON a.id=p.account_id
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN' AND t.expires_at>=:now
                        """)
                .param("userId", userId.toString())
                .param("now", clock.instant())
                .query(Integer.class)
                .single();
    }

    public int updateDipEvents(UUID userId) {
        return dipEvents.evaluateAndCapture(userId);
    }

    public int dailyDigest(UUID userId) {
        return jdbc.sql("SELECT COUNT(*) FROM recommendation WHERE user_id=UUID_TO_BIN(:userId) AND status='ACTIVE'")
                .param("userId", userId.toString())
                .query(Integer.class)
                .single();
    }

    public int weeklyMemo() {
        return jdbc.sql(
                        """
                        SELECT COUNT(*) FROM recommendation
                        WHERE status='ACTIVE' AND valid_until>=:now
                        """)
                .param("now", clock.instant())
                .query(Integer.class)
                .single();
    }

    public int monthlyReview() {
        return jdbc.sql(
                        """
                        SELECT COUNT(*) FROM portfolio_analysis_run
                        WHERE status='SUCCEEDED' AND completed_at>=:since
                        """)
                .param("since", clock.instant().minusSeconds(31L * 86400))
                .query(Integer.class)
                .single();
    }

    private Benchmark benchmark(String symbol, LocalDate date) {
        return jdbc.sql(
                        """
                        SELECT p.close_price latestClose,
                               (SELECT AVG(x.close_price) FROM (SELECT close_price FROM price_bar b
                                JOIN instrument j ON j.id=b.instrument_id WHERE j.symbol=:symbol
                                AND b.adjusted=TRUE AND b.market_date<=:date ORDER BY b.market_date DESC LIMIT 200) x) average200,
                               (SELECT value_double FROM indicator_snapshot s JOIN instrument k ON k.id=s.instrument_id
                                WHERE k.symbol=:symbol AND s.indicator_code='RSI_14' ORDER BY s.market_date DESC LIMIT 1) rsi,
                               (SELECT value_double FROM indicator_snapshot s JOIN instrument k ON k.id=s.instrument_id
                                WHERE k.symbol=:symbol AND s.indicator_code='MACD_12_26_9' ORDER BY s.market_date DESC LIMIT 1) macd,
                               (SELECT value_double FROM indicator_snapshot s JOIN instrument k ON k.id=s.instrument_id
                                WHERE k.symbol=:symbol AND s.indicator_code='REALIZED_VOL_20' ORDER BY s.market_date DESC LIMIT 1) realizedVolatility
                        FROM price_bar p JOIN instrument i ON i.id=p.instrument_id
                        WHERE i.symbol=:symbol AND p.adjusted=TRUE AND p.market_date<=:date
                        ORDER BY p.market_date DESC LIMIT 1
                        """)
                .param("symbol", symbol)
                .param("date", date)
                .query(BenchmarkRow.class)
                .optional()
                .map(row -> new Benchmark(
                        true,
                        row.average200() != null && row.latestClose().compareTo(row.average200()) >= 0,
                        row.rsi(),
                        row.macd(),
                        row.realizedVolatility()))
                .orElse(new Benchmark(false, false, null, null, null));
    }

    private double canonicalBreadth50(LocalDate date) {
        var value = breadthService.latest(date).pctAboveSma50();
        return value == null ? 0 : value.doubleValue();
    }

    private double returnFromPeak(String symbol, LocalDate date) {
        return jdbc.sql(
                        """
                        SELECT COALESCE(latest.close_price/MAX(p.close_price)-1,0)
                        FROM price_bar p JOIN instrument i ON i.id=p.instrument_id
                        JOIN price_bar latest ON latest.instrument_id=p.instrument_id AND latest.adjusted=TRUE
                          AND latest.market_date=(SELECT MAX(x.market_date) FROM price_bar x
                                                  WHERE x.instrument_id=p.instrument_id AND x.market_date<=:date)
                        WHERE i.symbol=:symbol AND p.adjusted=TRUE AND p.market_date<=:date
                        GROUP BY latest.close_price
                        """)
                .param("symbol", symbol)
                .param("date", date)
                .query(Double.class)
                .optional()
                .orElse(0.0);
    }

    private double stressLevel(LocalDate date) {
        var macroStress = macro.latestFactors(date).stressResilience();
        if (macroStress != null) return 1 - macroStress.doubleValue();
        var value = benchmark("QQQ", date).realizedVolatility();
        return value == null ? 0 : Math.clamp(value / 0.50, 0, 1);
    }

    private double largestClusterContribution(UUID userId, BigDecimal equity) {
        if (equity.signum() <= 0) return 0;
        var value = jdbc.sql(
                        """
                        SELECT COALESCE(MAX(cluster_value),0) FROM (
                          SELECT SUM(pm.marked_market_value) cluster_value FROM risk_cluster_membership m
                          JOIN risk_cluster c ON c.id=m.risk_cluster_id JOIN position p ON p.id=m.position_id
                          JOIN current_position_mark pm ON pm.position_id=p.id
                          WHERE c.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN' GROUP BY c.id
                        ) clusters
                        """)
                .param("userId", userId.toString())
                .query(BigDecimal.class)
                .single();
        return value.divide(equity, MathContext.DECIMAL64).doubleValue();
    }

    private static double score(boolean value) {
        return value ? 1 : 0;
    }

    private BigDecimal confirmedSwingLow(UUID instrumentId, LocalDate marketDate) {
        var bars = jdbc
                .sql(
                        """
                        SELECT market_date date,open_price open,high_price high,low_price low,close_price close,volume
                        FROM price_bar WHERE instrument_id=UUID_TO_BIN(:id) AND adjusted=TRUE AND market_date<=:date
                        ORDER BY market_date DESC LIMIT 260
                        """)
                .param("id", instrumentId.toString())
                .param("date", marketDate)
                .query(StopBar.class)
                .list()
                .reversed()
                .stream()
                .map(row ->
                        new QuantBar(row.date(), row.open(), row.high(), row.low(), row.close(), row.volume(), true))
                .toList();
        var confirmed = Indicators.confirmedSwingLow(bars, 2, 2)
                .value()
                .map(value -> BigDecimal.valueOf(value.price()))
                .orElse(null);
        if (confirmed != null || bars.size() < 5) return confirmed;
        return bars.subList(Math.max(0, bars.size() - 22), bars.size() - 2).stream()
                .map(QuantBar::low)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private static String json(java.util.List<String> values) {
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

    record PortfolioTotals(
            BigDecimal invested, BigDecimal cash, BigDecimal largest, long openPositions, long markCount) {}

    record StopInput(
            UUID positionId,
            UUID instrumentId,
            String classification,
            BigDecimal entryPrice,
            BigDecimal closePrice,
            double atr,
            double ema20,
            double rollingHigh,
            BigDecimal previousLiveStop) {}

    record StopBar(
            LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {}

    record BenchmarkRow(
            BigDecimal latestClose, BigDecimal average200, Double rsi, Double macd, Double realizedVolatility) {}

    record Benchmark(boolean available, boolean above200, Double rsi, Double macd, Double realizedVolatility) {}
}
