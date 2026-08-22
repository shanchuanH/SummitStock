package com.example.portfolio.analysis.risk;

import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioNavService {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private final JdbcClient jdbc;
    private final Clock clock;

    public PortfolioNavService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Snapshot capture(UUID userId, LocalDate marketDate, BigDecimal accountEquity) {
        return capture(userId, marketDate, accountEquity, clock.instant());
    }

    @Transactional
    public Snapshot capture(UUID userId, LocalDate marketDate, BigDecimal accountEquity, Instant dataAsOf) {
        if (accountEquity == null || accountEquity.signum() <= 0) {
            throw new IllegalArgumentException("Account equity must be positive");
        }
        var prior = jdbc.sql(
                        """
                        SELECT nav,units,high_water_nav highWaterNav,market_date marketDate,account_equity accountEquity,
                               data_as_of dataAsOf
                        FROM portfolio_nav_snapshot WHERE user_id=UUID_TO_BIN(:userId) AND market_date<:marketDate
                        ORDER BY market_date DESC,data_as_of DESC LIMIT 1
                        """)
                .param("userId", userId.toString())
                .param("marketDate", marketDate)
                .query(Prior.class)
                .optional();
        var strategyCapitalFlow = prior.map(value -> strategyCapitalFlow(userId, value.marketDate(), marketDate))
                .orElse(BigDecimal.ZERO);
        BigDecimal units;
        BigDecimal nav;
        BigDecimal highWaterNav;
        if (prior.isEmpty()) {
            units = accountEquity;
            nav = BigDecimal.ONE;
            highWaterNav = BigDecimal.ONE;
        } else {
            var value = prior.orElseThrow();
            units = value.units().add(strategyCapitalFlow.divide(value.nav(), MATH));
            if (units.signum() <= 0)
                throw new IllegalStateException("Strategy capital withdrawal exceeds portfolio units");
            nav = accountEquity.divide(units, MATH);
            highWaterNav = value.highWaterNav().max(nav);
        }
        var drawdown = highWaterNav.subtract(nav).divide(highWaterNav, MATH).max(BigDecimal.ZERO);
        var now = clock.instant();
        jdbc.sql(
                        """
                        INSERT INTO portfolio_nav_snapshot (
                          id,user_id,market_date,nav,units,external_cashflow,account_equity,high_water_nav,
                          drawdown_fraction,data_as_of,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),:marketDate,:nav,:units,:cashflow,:equity,
                          :highWater,:drawdown,:dataAsOf,:now)
                        ON DUPLICATE KEY UPDATE nav=VALUES(nav),units=VALUES(units),
                          external_cashflow=VALUES(external_cashflow),account_equity=VALUES(account_equity),
                          high_water_nav=VALUES(high_water_nav),drawdown_fraction=VALUES(drawdown_fraction),
                          data_as_of=VALUES(data_as_of)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("marketDate", marketDate)
                .param("nav", nav)
                .param("units", units)
                .param("cashflow", strategyCapitalFlow)
                .param("equity", accountEquity)
                .param("highWater", highWaterNav)
                .param("drawdown", drawdown)
                .param("dataAsOf", dataAsOf)
                .param("now", now)
                .update();
        var peak = peak(userId, highWaterNav, marketDate);
        return new Snapshot(
                marketDate,
                nav,
                units,
                strategyCapitalFlow,
                accountEquity,
                highWaterNav,
                drawdown,
                peak.marketDate(),
                peak.accountEquity(),
                dataAsOf);
    }

    @Transactional
    public boolean recordExternalCashflow(
            UUID userId, LocalDate effectiveDate, BigDecimal amount, String source, String externalReference) {
        if (amount == null || amount.signum() == 0) throw new IllegalArgumentException("Cashflow must be non-zero");
        var checksum = sha256(userId + "|" + effectiveDate + "|" + amount + "|" + source + "|" + externalReference);
        return jdbc.sql(
                                """
                        INSERT IGNORE INTO portfolio_external_cashflow_event (
                          id,user_id,effective_date,amount,source,external_reference,evidence_checksum,data_as_of,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),:date,:amount,:source,:reference,:checksum,:now,:now)
                        """)
                        .param("id", UUID.randomUUID().toString())
                        .param("userId", userId.toString())
                        .param("date", effectiveDate)
                        .param("amount", amount)
                        .param("source", source)
                        .param("reference", externalReference)
                        .param("checksum", checksum)
                        .param("now", clock.instant())
                        .update()
                == 1;
    }

    @Transactional
    public boolean recordStrategyCapitalFlow(
            UUID userId,
            LocalDate effectiveDate,
            BigDecimal amount,
            StrategyCapitalFlowType type,
            String source,
            String externalReference) {
        if (amount == null || amount.signum() == 0) {
            throw new IllegalArgumentException("Strategy capital flow must be non-zero");
        }
        if (type == null || type.direction() != amount.signum()) {
            throw new IllegalArgumentException("Strategy capital flow direction does not match its type");
        }
        var checksum = sha256(
                userId + "|" + effectiveDate + "|" + amount + "|" + type + "|" + source + "|" + externalReference);
        return jdbc.sql(
                                """
                        INSERT IGNORE INTO portfolio_strategy_capital_flow_event (
                          id,user_id,effective_date,amount,flow_type,source,external_reference,
                          evidence_checksum,data_as_of,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),:date,:amount,:type,:source,:reference,
                          :checksum,:now,:now)
                        """)
                        .param("id", UUID.randomUUID().toString())
                        .param("userId", userId.toString())
                        .param("date", effectiveDate)
                        .param("amount", amount)
                        .param("type", type.name())
                        .param("source", source)
                        .param("reference", externalReference)
                        .param("checksum", checksum)
                        .param("now", clock.instant())
                        .update()
                == 1;
    }

    private BigDecimal strategyCapitalFlow(UUID userId, LocalDate after, LocalDate through) {
        return jdbc.sql(
                        """
                        SELECT COALESCE(SUM(amount),0) FROM portfolio_strategy_capital_flow_event
                        WHERE user_id=UUID_TO_BIN(:userId) AND effective_date>:after AND effective_date<=:through
                        """)
                .param("userId", userId.toString())
                .param("after", after)
                .param("through", through)
                .query(BigDecimal.class)
                .single();
    }

    private Peak peak(UUID userId, BigDecimal highWaterNav, LocalDate through) {
        return jdbc.sql(
                        """
                        SELECT market_date marketDate,account_equity accountEquity
                        FROM portfolio_nav_snapshot WHERE user_id=UUID_TO_BIN(:userId)
                          AND market_date<=:through AND nav=:highWater
                        ORDER BY market_date DESC LIMIT 1
                        """)
                .param("userId", userId.toString())
                .param("through", through)
                .param("highWater", highWaterNav)
                .query(Peak.class)
                .optional()
                .orElse(new Peak(through, BigDecimal.ONE));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public enum StrategyCapitalFlowType {
        EXTERNAL_TO_STRATEGY(1),
        STRATEGY_TO_EXTERNAL(-1),
        EMERGENCY_TO_STRATEGY(1),
        STRATEGY_TO_EMERGENCY(-1);

        private final int direction;

        StrategyCapitalFlowType(int direction) {
            this.direction = direction;
        }

        int direction() {
            return direction;
        }
    }

    record Prior(
            BigDecimal nav,
            BigDecimal units,
            BigDecimal highWaterNav,
            LocalDate marketDate,
            BigDecimal accountEquity,
            LocalDateTime dataAsOf) {}

    record Peak(LocalDate marketDate, BigDecimal accountEquity) {}

    public record Snapshot(
            LocalDate marketDate,
            BigDecimal nav,
            BigDecimal units,
            BigDecimal strategyCapitalFlow,
            BigDecimal accountEquity,
            BigDecimal highWaterNav,
            BigDecimal drawdownFraction,
            LocalDate peakMarketDate,
            BigDecimal peakAccountEquity,
            Instant dataAsOf) {}
}
