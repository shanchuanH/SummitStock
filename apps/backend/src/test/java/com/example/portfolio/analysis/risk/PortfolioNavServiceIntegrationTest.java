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
    void depositsChangeUnitsButDoNotCreatePerformanceOrDrawdown() {
        var first = service.capture(USER, date("2026-08-10"), money("100000"));
        service.recordExternalCashflow(USER, date("2026-08-11"), money("20000"), "BROKER_DEPOSIT", "dep-1");
        var funded = service.capture(USER, date("2026-08-11"), money("120000"));
        var loss = service.capture(USER, date("2026-08-12"), money("108000"));

        assertThat(first.nav()).isEqualByComparingTo("1");
        assertThat(funded.nav()).isEqualByComparingTo("1");
        assertThat(funded.units()).isEqualByComparingTo("120000");
        assertThat(funded.externalCashflow()).isEqualByComparingTo("20000");
        assertThat(funded.drawdownFraction()).isZero();
        assertThat(loss.nav()).isEqualByComparingTo("0.9");
        assertThat(loss.drawdownFraction()).isEqualByComparingTo("0.1");
    }

    private static LocalDate date(String value) {
        return LocalDate.parse(value);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
