package com.example.portfolio.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiContractTest extends MySqlIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void versionHasRequestIdAndExportsOpenApiContract() throws Exception {
        mockMvc.perform(get("/api/v1/version").header(RequestIdFilter.HEADER, "request-123"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestIdFilter.HEADER, "request-123"))
                .andExpect(jsonPath("$.ruleIds[0]").value("CASH.EMERGENCY.001"))
                .andExpect(jsonPath("$.ruleIds[1]").value("COMPENSATION.UNVESTED.001"));

        var openApi = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        assertThat(openApi).contains("Portfolio Engine", "/api/v1/version", "/api/v1/auth/csrf");
        assertThat(openApi)
                .contains(
                        "/api/v1/instruments",
                        "/api/v1/instruments/{symbol}/bars",
                        "/api/v1/instruments/{symbol}/indicators",
                        "/api/v1/instruments/{symbol}/fundamentals",
                        "/api/v1/market/regime",
                        "/api/v1/portfolio/drawdown",
                        "/api/v1/market/data-health",
                        "/api/v1/accounts",
                        "/api/v1/portfolio/summary",
                        "/api/v1/positions",
                        "/api/v1/positions/{id}",
                        "/api/v1/holdings/analysis",
                        "/api/v1/actions/today",
                        "/api/v1/trade-plans/preview",
                        "/api/v1/positions/{positionId}/intelligence",
                        "/api/v1/positions/{positionId}/stops/preview",
                        "/api/v1/positions/{positionId}/thesis/confirm",
                        "/api/v1/positions/{positionId}/earnings/review",
                        "/api/v1/positions/{positionId}/journal",
                        "/api/v1/etf-dip/status",
                        "/api/v1/etf-dip/preview",
                        "/api/v1/cashflow/plan",
                        "/api/v1/active-sleeve/review",
                        "/api/v1/recommendations/history",
                        "/api/v1/recommendations/{id}/acknowledge",
                        "/api/v1/worker/health",
                        "/api/v1/backtests/latest",
                        "/api/v1/backtests/{id}");

        mockMvc.perform(get("/api/v1/instruments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].symbol").value("QQQ"))
                .andExpect(jsonPath("$.dataAsOf").isNotEmpty());
        mockMvc.perform(get("/api/v1/instruments/SPY/bars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/v1/instruments/SPY/indicators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/v1/instruments/SPY/fundamentals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
        mockMvc.perform(get("/api/v1/market/data-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").isString());

        var output = Path.of("..", "..", "contracts", "openapi", "portfolio-api.json")
                .toAbsolutePath()
                .normalize();
        Files.createDirectories(output.getParent());
        var formattedOpenApi = new ObjectMapper().readTree(openApi).toPrettyString();
        Files.writeString(output, formattedOpenApi + System.lineSeparator(), StandardCharsets.UTF_8);
    }
}
