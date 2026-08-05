package com.example.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class PortfolioApplicationTest extends MySqlIntegrationTest {
    @Autowired
    private JdbcClient jdbc;

    @Test
    void flywayCreatesFoundationAndSessionTables() {
        var tables = jdbc.sql(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                        """)
                .query(String.class)
                .list();

        assertThat(tables)
                .contains(
                        "app_user",
                        "strategy_version",
                        "investment_policy",
                        "cash_bucket",
                        "audit_log",
                        "instrument",
                        "price_bar",
                        "fundamental_observation",
                        "indicator_snapshot",
                        "market_regime_snapshot",
                        "portfolio_drawdown_snapshot",
                        "investment_account",
                        "equity_snapshot",
                        "position",
                        "position_lot",
                        "tax_lot",
                        "risk_cluster",
                        "position_risk_snapshot",
                        "holding_analysis_snapshot",
                        "recommendation",
                        "action_decision",
                        "position_thesis",
                        "thesis_source",
                        "stop_snapshot",
                        "stop_alert",
                        "valuation_snapshot",
                        "earnings_risk_snapshot",
                        "trade_journal",
                        "etf_dip_event",
                        "etf_dip_tranche",
                        "cashflow_allocation_snapshot",
                        "active_sleeve_review_snapshot",
                        "job_run",
                        "job_attempt",
                        "auth_security_event",
                        "recommendation_acknowledgement",
                        "backtest_run",
                        "backtest_metric",
                        "SPRING_SESSION",
                        "SPRING_SESSION_ATTRIBUTES");
    }

    @Test
    void databaseSessionUsesUtcAndStrictMode() {
        assertThat(jdbc.sql("SELECT @@session.time_zone").query(String.class).single())
                .isIn("+00:00", "UTC");
        assertThat(jdbc.sql("SELECT @@session.sql_mode").query(String.class).single())
                .contains("STRICT_TRANS_TABLES");
    }
}
