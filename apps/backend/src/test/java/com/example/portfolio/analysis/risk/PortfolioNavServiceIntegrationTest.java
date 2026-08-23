package com.example.portfolio.analysis.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class PortfolioNavServiceIntegrationTest extends MySqlIntegrationTest {
    private static final UUID USER = UUID.fromString("73000000-0000-0000-0000-000000000001");

    @Autowired
    private PortfolioNavService service;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void reset() {
        jdbc.sql("DELETE FROM portfolio_nav_snapshot WHERE user_id=UUID_TO_BIN(:id)")
                .param("id", USER.toString())
                .update();
        jdbc.sql("DELETE FROM portfolio_external_cashflow_event WHERE user_id=UUID_TO_BIN(:id)")
                .param("id", USER.toString())
                .update();
        jdbc.sql("DELETE FROM portfolio_strategy_capital_flow_event WHERE user_id=UUID_TO_BIN(:id)")
                .param("id", USER.toString())
                .update();
        jdbc.sql(
                        """
                        INSERT IGNORE INTO app_user (
                          id,email,password_hash,status,timezone,created_at,updated_at,version)
                        VALUES (UUID_TO_BIN(:id),'nav-owner@example.local','unused','ACTIVE','UTC',
                          UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)
                        """)
                .param("id", USER.toString())
                .update();
    }

    @Test
    void deployableDepositChangesUnitsButDoesNotCreatePerformanceOrDrawdown() {
        var first = service.capture(USER, date("2026-08-10"), money("100000"));
        service.recordExternalCashflow(USER, date("2026-08-11"), money("20000"), "BROKER_DEPOSIT", "dep-1");
        service.recordStrategyCapitalFlow(
                USER,
                date("2026-08-11"),
                money("20000"),
                PortfolioNavService.StrategyCapitalFlowType.EXTERNAL_TO_STRATEGY,
                "BROKER_DEPOSIT",
                "dep-1");
        var funded = service.capture(USER, date("2026-08-11"), money("120000"));
        var loss = service.capture(USER, date("2026-08-12"), money("108000"));

        assertThat(first.nav()).isEqualByComparingTo("1");
        assertThat(funded.nav()).isEqualByComparingTo("1");
        assertThat(funded.units()).isEqualByComparingTo("120000");
        assertThat(funded.strategyCapitalFlow()).isEqualByComparingTo("20000");
        assertThat(funded.drawdownFraction()).isZero();
        assertThat(loss.nav()).isEqualByComparingTo("0.9");
        assertThat(loss.drawdownFraction()).isEqualByComparingTo("0.1");
    }

    @Test
    void depositAllocatedOnlyToEmergencyDoesNotChangeStrategyUnitsOrReturn() {
        service.capture(USER, date("2026-08-10"), money("100000"));
        service.recordExternalCashflow(USER, date("2026-08-11"), money("20000"), "BROKER_DEPOSIT", "emergency");

        var unchanged = service.capture(USER, date("2026-08-11"), money("100000"));

        assertZeroReturn(unchanged, "100000", "0");
    }

    @Test
    void emergencyToDeployableIsAPositiveStrategyCapitalFlowNotPerformance() {
        service.capture(USER, date("2026-08-10"), money("100000"));
        service.recordStrategyCapitalFlow(
                USER,
                date("2026-08-11"),
                money("10000"),
                PortfolioNavService.StrategyCapitalFlowType.EMERGENCY_TO_STRATEGY,
                "OWNER_ALLOCATION",
                "transfer-in");

        assertZeroReturn(service.capture(USER, date("2026-08-11"), money("110000")), "110000", "10000");
    }

    @Test
    void deployableToEmergencyIsANegativeStrategyCapitalFlowNotPerformance() {
        service.capture(USER, date("2026-08-10"), money("100000"));
        service.recordStrategyCapitalFlow(
                USER,
                date("2026-08-11"),
                money("-10000"),
                PortfolioNavService.StrategyCapitalFlowType.STRATEGY_TO_EMERGENCY,
                "OWNER_ALLOCATION",
                "transfer-out");

        assertZeroReturn(service.capture(USER, date("2026-08-11"), money("90000")), "90000", "-10000");
    }

    @Test
    void deployableWithdrawalChangesUnitsButDoesNotCreatePerformance() {
        service.capture(USER, date("2026-08-10"), money("100000"));
        service.recordExternalCashflow(USER, date("2026-08-11"), money("-10000"), "BROKER_WITHDRAWAL", "wd-1");
        service.recordStrategyCapitalFlow(
                USER,
                date("2026-08-11"),
                money("-10000"),
                PortfolioNavService.StrategyCapitalFlowType.STRATEGY_TO_EXTERNAL,
                "BROKER_WITHDRAWAL",
                "wd-1");

        assertZeroReturn(service.capture(USER, date("2026-08-11"), money("90000")), "90000", "-10000");
    }

    @Test
    void marketMovementWithoutStrategyCapitalFlowChangesReturnAndHighWaterMark() {
        service.capture(USER, date("2026-08-10"), money("100000"));

        var gain = service.capture(USER, date("2026-08-11"), money("110000"));

        assertThat(gain.nav()).isEqualByComparingTo("1.1");
        assertThat(gain.highWaterNav()).isEqualByComparingTo("1.1");
        assertThat(gain.strategyCapitalFlow()).isZero();
        assertThat(gain.drawdownFraction()).isZero();
    }

    private static void assertZeroReturn(
            PortfolioNavService.Snapshot snapshot, String expectedUnits, String expectedCapitalFlow) {
        assertThat(snapshot.nav()).isEqualByComparingTo("1");
        assertThat(snapshot.units()).isEqualByComparingTo(expectedUnits);
        assertThat(snapshot.strategyCapitalFlow()).isEqualByComparingTo(expectedCapitalFlow);
        assertThat(snapshot.drawdownFraction()).isZero();
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
