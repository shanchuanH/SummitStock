package com.example.portfolio.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import com.example.portfolio.strategy.market.DrawdownEngine;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.market.MarketRegimeEngine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class MarketContextIntegrationTest extends MySqlIntegrationTest {
    @Autowired
    private MarketContextService service;

    @Autowired
    private MarketContextStore store;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void v3SnapshotsAreVersionedIdempotentAndExposedWithoutDecimalLoss() throws Exception {
        var tables = jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")
                .query(String.class)
                .list();
        assertThat(tables).contains("market_regime_snapshot", "portfolio_drawdown_snapshot");

        jdbc.sql(
                        """
                        INSERT IGNORE INTO app_user (
                            id,email,password_hash,status,timezone,created_at,updated_at,version
                        ) VALUES (
                            UUID_TO_BIN('11111111-1111-1111-1111-111111111111'), 'admin@example.local',
                            'unused','ACTIVE','UTC',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0
                        )
                        """)
                .update();

        var dataAsOf = Instant.parse("2026-08-04T21:00:00Z");
        var userId = jdbc.sql("SELECT BIN_TO_UUID(id) FROM app_user WHERE email='admin@example.local'")
                .query(UUID.class)
                .single();
        var regimeInput = new MarketRegimeEngine.Input(
                0.92, 0.88, 0.76, 0.82, false, false, 18, 0.61, false, 56, false, EvidenceQuality.HEALTHY);
        assertThat(service.calculateRegime(regimeInput, dataAsOf).inserted()).isTrue();
        assertThat(service.calculateRegime(regimeInput, dataAsOf).inserted()).isFalse();

        var drawdownInput = new DrawdownEngine.Input(
                new BigDecimal("85000.00"),
                new BigDecimal("100000.00"),
                -0.12,
                -0.14,
                0.28,
                0.80,
                0.20,
                0.35,
                EvidenceQuality.HEALTHY);
        assertThat(service.calculateDrawdown(
                                userId,
                                drawdownInput,
                                "[{\"symbol\":\"NVDA\",\"contribution\":\"0.20\"}]",
                                "[{\"cluster\":\"TECH\",\"contribution\":\"0.35\"}]",
                                dataAsOf)
                        .inserted())
                .isTrue();
        assertThat(service.calculateDrawdown(
                                userId,
                                drawdownInput,
                                "[{\"symbol\":\"NVDA\",\"contribution\":\"0.20\"}]",
                                "[{\"cluster\":\"TECH\",\"contribution\":\"0.35\"}]",
                                dataAsOf)
                        .inserted())
                .isFalse();

        assertThat(count("market_regime_snapshot")).isEqualTo(1);
        assertThat(count("portfolio_drawdown_snapshot")).isEqualTo(1);
        assertThat(store.latestRegime())
                .get()
                .extracting(MarketContextStore.RegimeView::strategyVersion)
                .isEqualTo("3.0.0-draft");
        assertThat(store.latestDrawdown("admin@example.local")).get().satisfies(value -> {
            assertThat(value.marketDriven()).isTrue();
            assertThat(value.sourceClassification()).isEqualTo("MARKET_DRIVEN");
        });

        mockMvc.perform(get("/api/v1/market/regime"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.snapshot.strategyVersion").value("3.0.0-draft"));
        mockMvc.perform(get("/api/v1/portfolio/drawdown").with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshot.drawdownFraction").value("0.15"))
                .andExpect(jsonPath("$.snapshot.drawdownPercent").value("15"))
                .andExpect(jsonPath("$.snapshot.marketDriven").value(true))
                .andExpect(jsonPath("$.snapshot.ruleIdsJson")
                        .value(org.hamcrest.Matchers.containsString("DRAWDOWN.MARKET.001")));
        mockMvc.perform(get("/api/v1/market/data-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staleObservations").isNumber())
                .andExpect(jsonPath("$.partialObservations").isNumber())
                .andExpect(jsonPath("$.suspectObservations").isNumber());
    }

    private long count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }
}
