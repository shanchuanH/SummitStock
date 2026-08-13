package com.example.portfolio.analysis.mark;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import com.example.portfolio.analysis.application.PositionSizing;
import com.example.portfolio.analysis.capital.CapitalBaseService;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.market.provider.TradingCalendar;
import com.example.portfolio.runtime.PortfolioAnalysisPipelineService;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class PositionMarkServiceIntegrationTest extends MySqlIntegrationTest {
    private static final UUID USER = UUID.fromString("b1000000-0000-0000-0000-000000000001");
    private static final UUID POSITION = UUID.fromString("b4000000-0000-0000-0000-000000000001");
    private static final UUID INSTRUMENT = UUID.fromString("b3000000-0000-0000-0000-000000000001");

    @Autowired
    JdbcClient jdbc;

    @Autowired
    PositionMarkService marks;

    @Autowired
    CapitalBaseService capital;

    @Autowired
    PortfolioAnalysisPipelineService pipeline;

    @Autowired
    TradingCalendar calendar;

    @Autowired
    Clock clock;

    private LocalDate completedSession;

    @BeforeEach
    void seed() {
        cleanup();
        completedSession = calendar.latestCompletedSession(clock.instant());
        update(
                """
                INSERT INTO app_user (id,email,password_hash,status,timezone,created_at,updated_at)
                VALUES (UUID_TO_BIN('b1000000-0000-0000-0000-000000000001'),'marks@example.local','x','ACTIVE','UTC',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO investment_account (id,user_id,account_key,institution,account_type,display_name,currency,active,created_at,updated_at)
                VALUES (UUID_TO_BIN('b2000000-0000-0000-0000-000000000001'),UUID_TO_BIN('b1000000-0000-0000-0000-000000000001'),'mark-test','Fidelity','BROKERAGE','Mark test','USD',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO instrument (id,symbol,exchange,asset_type,currency,active,metadata,created_at,updated_at)
                VALUES (UUID_TO_BIN('b3000000-0000-0000-0000-000000000001'),'MARK1','TEST','EQUITY','USD',TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO position (id,account_id,instrument_id,bucket,classification,classification_confirmed,
                  classification_source,quantity,average_cost,market_value,status,opened_at,created_at,updated_at,data_readiness)
                VALUES (UUID_TO_BIN('b4000000-0000-0000-0000-000000000001'),UUID_TO_BIN('b2000000-0000-0000-0000-000000000001'),
                  UUID_TO_BIN('b3000000-0000-0000-0000-000000000001'),'TACTICAL_OVERLAY','QUALITY_STOCK',TRUE,
                  'USER_CONFIRMED',10,90,1000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED')
                """);
        update(
                """
                INSERT INTO cash_bucket (id,user_id,bucket_type,target_amount,current_amount,currency,as_of,updated_at)
                VALUES (UUID_TO_BIN('b5000000-0000-0000-0000-000000000001'),UUID_TO_BIN('b1000000-0000-0000-0000-000000000001'),
                  'TACTICAL_RESERVE',1000,1000,'USD',CURRENT_DATE,UTC_TIMESTAMP(6))
                """);
    }

    @AfterEach
    void cleanup() {
        update(
                "DELETE FROM portfolio_drawdown_snapshot WHERE user_id=UUID_TO_BIN('b1000000-0000-0000-0000-000000000001')");
        update("DELETE FROM portfolio_nav_snapshot WHERE user_id=UUID_TO_BIN('b1000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM portfolio_external_cashflow_event WHERE user_id=UUID_TO_BIN('b1000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM portfolio_capital_snapshot WHERE user_id=UUID_TO_BIN('b1000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM position_mark_snapshot WHERE position_id=UUID_TO_BIN('b4000000-0000-0000-0000-000000000001')");
        update("DELETE FROM position WHERE account_id=UUID_TO_BIN('b2000000-0000-0000-0000-000000000001')");
        update("DELETE FROM cash_bucket WHERE user_id=UUID_TO_BIN('b1000000-0000-0000-0000-000000000001')");
        update("DELETE FROM price_bar WHERE instrument_id=UUID_TO_BIN('b3000000-0000-0000-0000-000000000001')");
        update("DELETE FROM instrument WHERE id=UUID_TO_BIN('b3000000-0000-0000-0000-000000000001')");
        update("DELETE FROM investment_account WHERE id=UUID_TO_BIN('b2000000-0000-0000-0000-000000000001')");
        update("DELETE FROM app_user WHERE id=UUID_TO_BIN('b1000000-0000-0000-0000-000000000001')");
    }

    @Test
    void completedAdjustedCloseRevaluesBrokerEvidenceAndCapital() {
        insertBar("b8000000-0000-0000-0000-000000000001", "100", "bar-100", clock.instant());

        assertThat(marks.captureForUser(USER, clock.instant()).healthy()).isTrue();
        assertThat(marks.latest(POSITION).orElseThrow().marketValue()).isEqualByComparingTo("1000");

        var later = clock.instant().plusSeconds(1);
        insertBar("b8000000-0000-0000-0000-000000000002", "120", "bar-120", later);
        assertThat(marks.captureForUser(USER, later).healthy()).isTrue();

        assertThat(marks.latest(POSITION).orElseThrow().marketValue()).isEqualByComparingTo("1200");
        assertThat(capital.calculate(USER).investedTradableAssets()).isEqualByComparingTo("1200");
        assertThat(capital.calculate(USER).investableAssets()).isEqualByComparingTo("2200");
        assertThat(jdbc.sql("SELECT market_value FROM position WHERE id=UUID_TO_BIN(:id)")
                        .param("id", POSITION.toString())
                        .query(BigDecimal.class)
                        .single())
                .as("broker evidence remains unchanged")
                .isEqualByComparingTo("1000");
    }

    @Test
    void missingCanonicalMarkMakesCapitalNonHealthyAndBlocksExactSizing() {
        var current = capital.calculate(USER);
        assertThat(current.investedTradableAssets()).isZero();
        assertThat(current.quality()).isEqualTo(EvidenceQuality.MISSING);

        var result = PositionSizing.calculate(new PositionSizing.Input(
                RecommendationAction.ADD,
                new BigDecimal("1000"),
                new BigDecimal("0.005"),
                new BigDecimal("100"),
                new BigDecimal("90"),
                new BigDecimal("100"),
                new BigDecimal("10"),
                new BigDecimal("1000"),
                new BigDecimal("0.10"),
                new BigDecimal("0.12"),
                null,
                new BigDecimal("1000"),
                BigDecimal.ZERO,
                new BigDecimal("0.0075"),
                new BigDecimal("0.50"),
                true,
                EvidenceQuality.HEALTHY,
                current.quality(),
                EvidenceQuality.HEALTHY,
                true,
                true,
                true,
                false));

        assertThat(result.exactQuantityAllowed()).isFalse();
    }

    @Test
    void priceIncreaseChangesHardCapWeightAndPortfolioEquity() {
        insertBar("b8000000-0000-0000-0000-000000000001", "100", "bar-100", clock.instant());
        marks.captureForUser(USER, clock.instant());
        pipeline.computeDrawdown(USER, completedSession);

        assertThat(currentWeight()).isEqualByComparingTo("0.5");
        assertThat(latestDrawdownEquity()).isEqualByComparingTo("2000");

        var later = clock.instant().plusSeconds(1);
        insertBar("b8000000-0000-0000-0000-000000000002", "120", "bar-120", later);
        marks.captureForUser(USER, later);
        pipeline.computeDrawdown(USER, completedSession);

        assertThat(currentWeight())
                .as("a 20%% price rise crosses a hypothetical 53%% hard cap")
                .isGreaterThan(new BigDecimal("0.53"));
        assertThat(latestDrawdownEquity())
                .as("portfolio equity is recomputed from the canonical mark")
                .isEqualByComparingTo("2200");
    }

    private BigDecimal currentWeight() {
        var current = capital.calculate(USER);
        return marks.latest(POSITION)
                .orElseThrow()
                .marketValue()
                .divide(current.investableAssets(), java.math.MathContext.DECIMAL64);
    }

    private BigDecimal latestDrawdownEquity() {
        return jdbc.sql(
                        """
                        SELECT current_equity FROM portfolio_drawdown_snapshot
                        WHERE user_id=UUID_TO_BIN(:userId)
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("userId", USER.toString())
                .query(BigDecimal.class)
                .single();
    }

    private void insertBar(String id, String close, String checksumSeed, Instant dataAsOf) {
        jdbc.sql(
                        """
                        INSERT INTO price_bar (id,instrument_id,timeframe,bar_start,market_date,open_price,high_price,
                          low_price,close_price,volume,adjusted,provider,source_timestamp,checksum,
                          normalization_version,quality_status,data_as_of,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:instrument),'1D',:asOf,:date,:close,:close,:close,:close,
                          1000,TRUE,:provider,:asOf,SHA2(:checksum,256),'v1','HEALTHY',:asOf,:asOf)
                        """)
                .param("id", id)
                .param("instrument", INSTRUMENT.toString())
                .param("asOf", dataAsOf)
                .param("date", completedSession)
                .param("close", new BigDecimal(close))
                .param("provider", id.endsWith("1") ? "TEST_A" : "TEST_B")
                .param("checksum", checksumSeed)
                .update();
    }

    private void update(String sql) {
        jdbc.sql(sql).update();
    }
}
