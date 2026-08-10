package com.example.portfolio.market;

import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.quant.Indicators;
import com.example.portfolio.quant.QuantBar;
import com.example.portfolio.strategy.position.PriceStateEngine;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class PriceStateApplicationService {
    private final JdbcClient jdbc;
    private final PortfolioProperties properties;
    private final Clock clock;
    private final PriceStateEngine engine = new PriceStateEngine();

    public PriceStateApplicationService(JdbcClient jdbc, PortfolioProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    public int computeAll(LocalDate marketDate) {
        var benchmark = bars(symbolId("SPY"), marketDate);
        int affected = 0;
        for (var instrument : jdbc.sql(
                        "SELECT BIN_TO_UUID(id) id FROM instrument WHERE active=TRUE AND asset_type IN ('EQUITY','ETF')")
                .query(UUID.class)
                .list()) {
            var bars = bars(instrument, marketDate);
            if (bars.isEmpty()) continue;
            var input = input(bars, benchmark);
            var result = engine.evaluate(input.engineInput());
            var checksum = sha256(instrument + "|" + marketDate + "|" + input + "|" + result);
            affected += jdbc.sql(
                            """
                            INSERT IGNORE INTO price_state_snapshot (
                              id,instrument_id,market_date,price_state,reversal_confirmations,relative_strength,
                              rolling_high_drawdown,strategy_version,evidence_checksum,data_as_of,created_at
                            ) VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:marketDate,:state,:confirmations,
                              :relativeStrength,:drawdown,:strategy,:checksum,:now,:now)
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("instrumentId", instrument.toString())
                    .param("marketDate", marketDate)
                    .param("state", result.state().name())
                    .param("confirmations", result.reversalConfirmations())
                    .param("relativeStrength", input.relativeStrength())
                    .param("drawdown", input.drawdown())
                    .param("strategy", properties.strategyVersion())
                    .param("checksum", checksum)
                    .param("now", clock.instant())
                    .update();
        }
        return affected;
    }

    private StateInput input(List<QuantBar> bars, List<QuantBar> benchmark) {
        var current = bars.getLast();
        var previousBars = bars.size() > 1 ? bars.subList(0, bars.size() - 1) : List.<QuantBar>of();
        var sma20 = value(Indicators.sma(bars, 20));
        var sma50 = value(Indicators.sma(bars, 50));
        var sma200 = value(Indicators.sma(bars, 200));
        var previousSma20 = value(Indicators.sma(previousBars, 20));
        var rsi = value(Indicators.rsi(bars, 14));
        var previousRsi = value(Indicators.rsi(previousBars, 14));
        var macd = Indicators.macd(bars, 12, 26, 9).value().orElse(null);
        var previousMacd = Indicators.macd(previousBars, 12, 26, 9).value().orElse(null);
        var rollingHigh = value(Indicators.rollingHigh(bars, 63));
        var relative = value(Indicators.relativeStrength(bars, benchmark, 63));
        var complete =
                sma20 != null && sma50 != null && sma200 != null && rsi != null && macd != null && relative != null;
        var drawdown = rollingHigh == null
                ? null
                : current.close()
                        .divide(BigDecimal.valueOf(rollingHigh), MathContext.DECIMAL128)
                        .subtract(BigDecimal.ONE);
        var engineInput = new PriceStateEngine.Input(
                current.close().doubleValue(),
                zero(sma20),
                zero(sma50),
                zero(sma200),
                zero(rsi),
                macd == null ? 0 : macd.histogram(),
                drawdown == null ? 0 : drawdown.doubleValue(),
                zero(relative),
                previousSma20 != null
                        && previousBars.getLast().close().doubleValue() < previousSma20
                        && current.close().doubleValue() >= zero(sma20),
                higherLow(bars),
                previousMacd != null && macd != null && previousMacd.histogram() <= 0 && macd.histogram() > 0,
                previousRsi != null && rsi != null && previousRsi < 40 && rsi >= 40,
                relative != null && relative >= 0.98,
                highVolumeReversal(bars),
                complete);
        return new StateInput(engineInput, relative == null ? null : BigDecimal.valueOf(relative), drawdown);
    }

    private static boolean higherLow(List<QuantBar> bars) {
        if (bars.size() < 10) return false;
        var recent = bars.subList(bars.size() - 5, bars.size()).stream()
                .mapToDouble(b -> b.low().doubleValue())
                .min()
                .orElseThrow();
        var prior = bars.subList(bars.size() - 10, bars.size() - 5).stream()
                .mapToDouble(b -> b.low().doubleValue())
                .min()
                .orElseThrow();
        return recent > prior;
    }

    private static boolean highVolumeReversal(List<QuantBar> bars) {
        if (bars.size() < 21) return false;
        var current = bars.getLast();
        var average = bars.subList(bars.size() - 21, bars.size() - 1).stream()
                .mapToDouble(bar -> bar.volume().doubleValue())
                .average()
                .orElse(0);
        return current.close().compareTo(current.open()) > 0 && current.volume().doubleValue() > average * 1.5;
    }

    private List<QuantBar> bars(UUID instrumentId, LocalDate marketDate) {
        if (instrumentId == null) return List.of();
        return jdbc
                .sql(
                        """
                        SELECT market_date date,open_price open,high_price high,low_price low,close_price close,volume
                        FROM price_bar WHERE instrument_id=UUID_TO_BIN(:id) AND adjusted=TRUE AND market_date<=:date
                        ORDER BY market_date DESC LIMIT 260
                        """)
                .param("id", instrumentId.toString())
                .param("date", marketDate)
                .query(BarRow.class)
                .list()
                .reversed()
                .stream()
                .map(row ->
                        new QuantBar(row.date(), row.open(), row.high(), row.low(), row.close(), row.volume(), true))
                .toList();
    }

    private UUID symbolId(String symbol) {
        return jdbc.sql("SELECT BIN_TO_UUID(id) FROM instrument WHERE symbol=:symbol AND active=TRUE LIMIT 1")
                .param("symbol", symbol)
                .query(UUID.class)
                .optional()
                .orElse(null);
    }

    private static Double value(com.example.portfolio.quant.IndicatorResult<Double> value) {
        return value.value().orElse(null);
    }

    private static double zero(Double value) {
        return value == null ? 0 : value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record BarRow(
            LocalDate date, BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {}

    record StateInput(PriceStateEngine.Input engineInput, BigDecimal relativeStrength, BigDecimal drawdown) {}
}
