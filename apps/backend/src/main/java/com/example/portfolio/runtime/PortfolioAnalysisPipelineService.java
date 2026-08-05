package com.example.portfolio.runtime;

import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.context.MarketContextService;
import com.example.portfolio.strategy.market.DrawdownEngine;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.market.MarketRegimeEngine;
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
    private final Clock clock;

    public PortfolioAnalysisPipelineService(
            JdbcClient jdbc, MarketContextService contextService, PortfolioProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.contextService = contextService;
        this.properties = properties;
        this.clock = clock;
    }

    public int collectBreadthMacro(LocalDate marketDate) {
        return jdbc.sql(
                        """
                        SELECT COUNT(*) FROM instrument i
                        WHERE i.active=TRUE AND i.asset_type IN ('EQUITY','ETF')
                          AND EXISTS (SELECT 1 FROM price_bar p WHERE p.instrument_id=i.id
                                      AND p.adjusted=TRUE AND p.market_date<=:marketDate)
                        """)
                .param("marketDate", marketDate)
                .query(Integer.class)
                .single();
    }

    public int computeRegime(LocalDate marketDate) {
        var spy = benchmark("SPY", marketDate);
        var qqq = benchmark("QQQ", marketDate);
        var breadth = breadth(marketDate);
        var quality = spy.available() && qqq.available() ? EvidenceQuality.PARTIAL : EvidenceQuality.MISSING;
        double trend = (score(spy.above200()) + score(qqq.above200())) / 2.0;
        double momentum = qqq.rsi() == null ? 0 : Math.clamp(qqq.rsi() / 100.0, 0, 1);
        double stressResilience =
                qqq.realizedVolatility() == null ? 0 : 1 - Math.clamp(qqq.realizedVolatility() / 0.50, 0, 1);
        var input = new MarketRegimeEngine.Input(
                trend,
                momentum,
                breadth,
                stressResilience,
                spy.available() && !spy.above200(),
                qqq.available() && !qqq.above200(),
                0,
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
        return jdbc.sql(
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
    }

    public int computeDrawdown(UUID userId, LocalDate marketDate) {
        var totals = jdbc.sql(
                        """
                        SELECT COALESCE(SUM(p.market_value),0) invested,
                               COALESCE((SELECT SUM(c.current_amount) FROM cash_bucket c
                                         WHERE c.user_id=UUID_TO_BIN(:userId)),0) cash,
                               COALESCE(MAX(p.market_value),0) largest
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        """)
                .param("userId", userId.toString())
                .query(PortfolioTotals.class)
                .single();
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
                breadth(marketDate),
                stressLevel(marketDate),
                totals.largest().divide(equity, MathContext.DECIMAL64).doubleValue(),
                0,
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
                        SELECT BIN_TO_UUID(p.id) position_id, COALESCE(p.average_cost,b.close_price) entry_price,
                               b.close_price, atr.value_double atr
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN instrument i ON i.id=p.instrument_id
                        JOIN price_bar b ON b.instrument_id=i.id AND b.adjusted=TRUE
                          AND b.market_date=(SELECT MAX(x.market_date) FROM price_bar x
                                             WHERE x.instrument_id=i.id AND x.adjusted=TRUE AND x.market_date<=:marketDate)
                        JOIN indicator_snapshot atr ON atr.instrument_id=i.id AND atr.indicator_code='ATR_14'
                          AND atr.market_date=b.market_date AND atr.status='READY'
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN' AND i.asset_type='EQUITY'
                        """)
                .param("marketDate", marketDate)
                .param("userId", userId.toString())
                .query(StopInput.class)
                .list()) {
            var atr = BigDecimal.valueOf(row.atr());
            var structure =
                    row.closePrice().subtract(atr.multiply(BigDecimal.TWO)).max(new BigDecimal("0.0001"));
            var initial = structure
                    .min(row.entryPrice().multiply(new BigDecimal("0.92")))
                    .max(new BigDecimal("0.0001"));
            var live = jdbc.sql("SELECT MAX(live_stop) FROM stop_snapshot WHERE position_id=UUID_TO_BIN(:id)")
                    .param("id", row.positionId().toString())
                    .query(BigDecimal.class)
                    .optional()
                    .orElse(initial)
                    .max(initial);
            var checksum = sha256(row + ":" + marketDate);
            affected += jdbc.sql(
                            """
                            INSERT IGNORE INTO stop_snapshot (
                                id, position_id, strategy_version, entry_price, atr, structure_stop,
                                volatility_stop, initial_stop, live_stop, soft_alert, catastrophic_stop,
                                close_confirmed, rule_ids, quality_status, evidence_checksum, data_as_of, created_at
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:positionId), :strategy, :entry, :atr, :structure,
                                :structure, :initial, :live, :soft, :catastrophic, FALSE,
                                JSON_ARRAY('STOP.EOD.001'), 'HEALTHY', :checksum, :now, :now
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("positionId", row.positionId().toString())
                    .param("strategy", properties.strategyVersion())
                    .param("entry", row.entryPrice())
                    .param("atr", atr)
                    .param("structure", structure)
                    .param("initial", initial)
                    .param("live", live)
                    .param("soft", live.multiply(new BigDecimal("1.02")))
                    .param("catastrophic", live.multiply(new BigDecimal("0.90")))
                    .param("checksum", checksum)
                    .param("now", clock.instant())
                    .update();
        }
        return affected;
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
        return jdbc.sql("SELECT COUNT(*) FROM etf_dip_event WHERE user_id=UUID_TO_BIN(:userId) AND valid_until>=:now")
                .param("userId", userId.toString())
                .param("now", clock.instant())
                .query(Integer.class)
                .single();
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

    private double breadth(LocalDate date) {
        return jdbc.sql(
                        """
                        WITH ranked AS (
                            SELECT instrument_id, close_price,
                                   ROW_NUMBER() OVER (PARTITION BY instrument_id ORDER BY market_date DESC) rn
                            FROM price_bar WHERE adjusted=TRUE AND market_date<=:date
                        ), evidence AS (
                            SELECT instrument_id,
                                   MAX(CASE WHEN rn=1 THEN close_price END) latest_close,
                                   AVG(CASE WHEN rn<=50 THEN close_price END) average_50
                            FROM ranked GROUP BY instrument_id
                        )
                        SELECT COALESCE(AVG(CASE WHEN latest_close>average_50 THEN 1 ELSE 0 END),0) FROM evidence
                        """)
                .param("date", date)
                .query(Double.class)
                .single();
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
        var value = benchmark("QQQ", date).realizedVolatility();
        return value == null ? 0 : Math.clamp(value / 0.50, 0, 1);
    }

    private static double score(boolean value) {
        return value ? 1 : 0;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record PortfolioTotals(BigDecimal invested, BigDecimal cash, BigDecimal largest) {}

    record StopInput(UUID positionId, BigDecimal entryPrice, BigDecimal closePrice, double atr) {}

    record BenchmarkRow(
            BigDecimal latestClose, BigDecimal average200, Double rsi, Double macd, Double realizedVolatility) {}

    record Benchmark(boolean available, boolean above200, Double rsi, Double macd, Double realizedVolatility) {}
}
