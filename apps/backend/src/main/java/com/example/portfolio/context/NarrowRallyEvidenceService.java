package com.example.portfolio.context;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class NarrowRallyEvidenceService {
    private final JdbcClient jdbc;
    private final BreadthService breadth;
    private final NarrowRallyEngine engine = new NarrowRallyEngine();

    public NarrowRallyEvidenceService(JdbcClient jdbc, BreadthService breadth) {
        this.jdbc = jdbc;
        this.breadth = breadth;
    }

    public Evidence evaluate(LocalDate marketDate, boolean spyAbove200, boolean qqqAbove200) {
        var priorDate = benchmarkSession(marketDate, 20);
        var currentBreadth = breadth.latest(marketDate);
        var priorBreadth = priorDate == null ? null : breadth.latest(priorDate);
        var input = new NarrowRallyEngine.Input(
                spyAbove200,
                qqqAbove200,
                currentBreadth.pctAboveSma50(),
                currentBreadth.pctAboveSma200(),
                priorBreadth == null ? null : priorBreadth.pctAboveSma50(),
                priorBreadth == null ? null : priorBreadth.pctAboveSma200(),
                newHighParticipation(marketDate),
                priorDate == null ? null : newHighParticipation(priorDate),
                relativeReturn("RSP", "SPY", marketDate, 63));
        return new Evidence(input, engine.evaluate(input), priorDate);
    }

    private LocalDate benchmarkSession(LocalDate marketDate, int sessionsAgo) {
        return jdbc.sql(
                        """
                        SELECT p.market_date FROM price_bar p JOIN instrument i ON i.id=p.instrument_id
                        WHERE i.symbol='SPY' AND p.adjusted=TRUE AND p.market_date<=:date
                        ORDER BY p.market_date DESC LIMIT 1 OFFSET :offset
                        """)
                .param("date", marketDate)
                .param("offset", sessionsAgo)
                .query(LocalDate.class)
                .optional()
                .orElse(null);
    }

    private BigDecimal newHighParticipation(LocalDate marketDate) {
        return jdbc.sql(
                        """
                        WITH members AS (
                          SELECT DISTINCT m.instrument_id FROM breadth_universe_member m
                          WHERE m.valid_from<=:date AND (m.valid_to IS NULL OR m.valid_to>=:date)
                        ), ranked AS (
                          SELECT p.instrument_id,p.close_price,
                                 ROW_NUMBER() OVER (PARTITION BY p.instrument_id ORDER BY p.market_date DESC,p.data_as_of DESC) rn
                          FROM price_bar p JOIN members m ON m.instrument_id=p.instrument_id
                          WHERE p.adjusted=TRUE AND p.market_date<=:date
                        ), stats AS (
                          SELECT instrument_id,COUNT(*) observations,
                                 MAX(CASE WHEN rn=1 THEN close_price END) latest_close,
                                 MAX(CASE WHEN rn BETWEEN 2 AND 253 THEN close_price END) prior_high
                          FROM ranked WHERE rn<=253 GROUP BY instrument_id
                        )
                        SELECT AVG(CASE WHEN observations>=253 THEN latest_close>=prior_high END) FROM stats
                        """)
                .param("date", marketDate)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    private BigDecimal relativeReturn(String equalWeight, String capWeight, LocalDate marketDate, int sessions) {
        return jdbc.sql(
                        """
                        WITH ranked AS (
                          SELECT i.symbol,p.close_price,
                                 ROW_NUMBER() OVER (PARTITION BY i.symbol ORDER BY p.market_date DESC,p.data_as_of DESC) rn
                          FROM price_bar p JOIN instrument i ON i.id=p.instrument_id
                          WHERE i.symbol IN (:equalWeight,:capWeight) AND p.adjusted=TRUE AND p.market_date<=:date
                        ), returns AS (
                          SELECT symbol,
                                 MAX(CASE WHEN rn=1 THEN close_price END) /
                                 NULLIF(MAX(CASE WHEN rn=:priorRow THEN close_price END),0)-1 period_return
                          FROM ranked WHERE rn<=:priorRow GROUP BY symbol
                        )
                        SELECT MAX(CASE WHEN symbol=:equalWeight THEN period_return END) -
                               MAX(CASE WHEN symbol=:capWeight THEN period_return END)
                        FROM returns HAVING COUNT(period_return)=2
                        """)
                .param("equalWeight", equalWeight)
                .param("capWeight", capWeight)
                .param("date", marketDate)
                .param("priorRow", sessions + 1)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    public record Evidence(NarrowRallyEngine.Input input, NarrowRallyEngine.Result result, LocalDate priorDate) {}
}
