package com.example.portfolio.reporting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BacktestReportIntegrationTest extends MySqlIntegrationTest {
    private static final String OWNER_ID = "33333333-3333-3333-3333-333333333333";
    private static final String OTHER_ID = "44444444-4444-4444-4444-444444444444";

    @Autowired
    BacktestReportStore reports;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void seed() {
        jdbc.sql("DELETE FROM backtest_metric").update();
        jdbc.sql("DELETE FROM backtest_run").update();
        jdbc.sql("DELETE FROM app_user WHERE id IN (UUID_TO_BIN(:owner), UUID_TO_BIN(:other))")
                .param("owner", OWNER_ID)
                .param("other", OTHER_ID)
                .update();
        jdbc.sql(
                        """
                INSERT IGNORE INTO app_user (id, email, password_hash, status, timezone, created_at, updated_at, version)
                VALUES (UUID_TO_BIN(:owner), 'admin@example.local', 'unused', 'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
                       (UUID_TO_BIN(:other), 'other-backtest@example.local', 'unused', 'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """)
                .param("owner", OWNER_ID)
                .param("other", OTHER_ID)
                .update();
    }

    @AfterEach
    void clean() {
        jdbc.sql("DELETE FROM backtest_metric").update();
        jdbc.sql("DELETE FROM backtest_run").update();
        jdbc.sql("DELETE FROM app_user WHERE id IN (UUID_TO_BIN(:owner), UUID_TO_BIN(:other))")
                .param("owner", OWNER_ID)
                .param("other", OTHER_ID)
                .update();
    }

    @Test
    void persistsIdempotentScopedReportsAndServesReadOnlyApi() throws Exception {
        var runId = UUID.randomUUID();
        var metrics = List.of(
                new BacktestReportStore.Metric("HOLD_FREQUENCY", .72, 100, "ALL", -1),
                new BacktestReportStore.Metric("RECOMMENDATION_RETURN", .04, 80, "ALL", 21));
        var saved = reports.saveCompleted(
                "admin@example.local",
                runId,
                "v1:2020-2025:abc",
                "1.0.0-draft",
                LocalDate.parse("2020-01-01"),
                LocalDate.parse("2025-12-31"),
                LocalDate.parse("2023-12-31"),
                LocalDate.parse("2024-01-01"),
                "a".repeat(64),
                "b".repeat(64),
                "{\"trades\":42}",
                metrics,
                Instant.parse("2026-08-05T12:00:00Z"));
        assertThat(saved).isTrue();
        assertThat(reports.saveCompleted(
                        "admin@example.local",
                        UUID.randomUUID(),
                        "v1:2020-2025:abc",
                        "1.0.0-draft",
                        LocalDate.parse("2020-01-01"),
                        LocalDate.parse("2025-12-31"),
                        null,
                        null,
                        "a".repeat(64),
                        "b".repeat(64),
                        "{}",
                        List.of(),
                        Instant.now()))
                .isFalse();
        assertThat(reports.find("other-backtest@example.local", runId)).isEmpty();
        assertThat(reports.latest("admin@example.local"))
                .hasValueSatisfying(run -> assertThat(run.metrics()).hasSize(2));

        mockMvc.perform(get("/api/v1/backtests/latest").with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.biasStatus").value("CLEAR"))
                .andExpect(jsonPath("$.metrics[0].name").value("HOLD_FREQUENCY"));
        mockMvc.perform(get("/api/v1/backtests/latest")).andExpect(status().isUnauthorized());
    }
}
