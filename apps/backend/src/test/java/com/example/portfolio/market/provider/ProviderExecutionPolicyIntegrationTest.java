package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import com.example.portfolio.market.persistence.ProviderRequestJournal;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class ProviderExecutionPolicyIntegrationTest extends MySqlIntegrationTest {
    @Autowired
    ProviderRequestJournal journal;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    Clock clock;

    @AfterEach
    void clean() {
        jdbc.sql("DELETE FROM provider_request WHERE provider='policy-contract'")
                .update();
        jdbc.sql("DELETE FROM provider_usage_daily WHERE provider_id='policy-contract'")
                .update();
    }

    @Test
    void everyHttpCallUsesCentralRetryQuotaAndSanitizedJournal() throws Exception {
        var attempts = new AtomicInteger();
        try (var server = new ProviderMockServer(exchange -> attempts.incrementAndGet() == 1
                ? new ProviderMockServer.Response(429, "{\"error\":\"slow down\"}")
                : new ProviderMockServer.Response(200, "{\"ok\":true}"))) {
            var policy = new ProviderExecutionPolicy(
                    clock, ignored -> {}, 2, Duration.ZERO, Duration.ZERO, journal, "policy-contract");
            var client = new ProviderHttpClient(
                    HttpClient.newHttpClient(), policy, Duration.ofSeconds(2), new ObjectMapper());

            var response = client.get(
                    URI.create(server.baseUrl() + "/query?function=GLOBAL_QUOTE&apikey=super-secret"), Map.of());
            assertThat(response.json().path("ok").asBoolean()).isTrue();
        }

        var request = jdbc.sql(
                        "SELECT operation,status,request_context context FROM provider_request WHERE provider='policy-contract'")
                .query(RequestRow.class)
                .single();
        assertThat(request.operation()).isEqualTo("global_quote");
        assertThat(request.status()).isEqualTo("SUCCEEDED");
        assertThat(request.context()).doesNotContain("super-secret").doesNotContain("apikey");
        assertThat(attempts).hasValue(2);
        assertThat(jdbc.sql("SELECT request_count FROM provider_usage_daily WHERE provider_id='policy-contract'")
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
    }

    record RequestRow(String operation, String status, String context) {}
}
