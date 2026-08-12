package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class MarketProviderContractTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T21:30:00Z"), ZoneOffset.UTC);

    @Test
    void normalizesBarsQuoteAndCorporateActionsWithProvenance() throws Exception {
        try (var server = new ProviderMockServer(exchange -> {
            var query = exchange.getRequestURI().getRawQuery();
            if (query.contains("TIME_SERIES_DAILY_ADJUSTED")) return ok(dailyPayload());
            if (query.contains("GLOBAL_QUOTE")) return ok(quotePayload());
            if (query.contains("DIVIDENDS")) return ok(dividendPayload());
            if (query.contains("SPLITS")) return ok(splitPayload());
            return new ProviderMockServer.Response(404, "{}");
        })) {
            var provider = provider(server.baseUrl() + "/query", 1);
            var bars = provider.fetchDailyBars("msft", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-05"));
            assertThat(bars.symbol()).isEqualTo("MSFT");
            assertThat(bars.bars()).hasSize(2);
            assertThat(bars.bars().getFirst().adjusted()).isTrue();
            assertThat(bars.bars().getLast().close()).isEqualByComparingTo("102.00");
            assertThat(bars.provenance().provider()).isEqualTo("alpha-vantage");
            assertThat(bars.provenance().checksum()).hasSize(64);
            assertThat(bars.provenance().fetchedAt()).isEqualTo(CLOCK.instant());
            assertThat(bars.provenance().warnings()).contains("OHLC_ADJUSTED_USING_PROVIDER_CLOSE_FACTOR");

            var quote = provider.fetchQuote("MSFT");
            assertThat(quote.last()).isEqualByComparingTo("101.25");
            assertThat(quote.decisionPrice().quality()).isEqualTo(ProviderModels.QualityStatus.HEALTHY);
            assertThat(quote.executionLiquidity().quality()).isEqualTo(ProviderModels.QualityStatus.MISSING);
            assertThat(quote.provenance().qualityStatus()).isEqualTo(ProviderModels.QualityStatus.HEALTHY);

            var actions = provider.fetchCorporateActions(
                    "MSFT", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-12-31"));
            assertThat(actions.actions())
                    .extracting(ProviderModels.CorporateAction::type)
                    .containsExactly("DIVIDEND", "SPLIT");
            assertThat(actions.provenance().qualityStatus()).isEqualTo(ProviderModels.QualityStatus.HEALTHY);
        }
    }

    @Test
    void retriesRateLimitsAndRejectsMalformedPayloads() throws Exception {
        var attempts = new AtomicInteger();
        try (var server = new ProviderMockServer(exchange -> attempts.incrementAndGet() == 1
                ? new ProviderMockServer.Response(429, "{\"error\":\"slow down\"}")
                : ok(dailyPayload()))) {
            var result = provider(server.baseUrl() + "/query", 2)
                    .fetchDailyBars("MSFT", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-05"));
            assertThat(result.bars()).hasSize(2);
            assertThat(attempts).hasValue(2);
        }
        try (var server = new ProviderMockServer(exchange -> ok("not-json"))) {
            assertThatThrownBy(() -> provider(server.baseUrl() + "/query", 1).fetchQuote("MSFT"))
                    .isInstanceOf(ProviderCallException.class)
                    .extracting(error -> ((ProviderCallException) error).code())
                    .isEqualTo("PROVIDER_MALFORMED");
        }
    }

    @Test
    void emptyBarsAreMissingRatherThanValid() throws Exception {
        try (var server = new ProviderMockServer(exchange -> ok("{\"Time Series (Daily)\":{}}"))) {
            var result = provider(server.baseUrl() + "/query", 1)
                    .fetchDailyBars("MSFT", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-05"));
            assertThat(result.bars()).isEmpty();
            assertThat(result.provenance().qualityStatus()).isEqualTo(ProviderModels.QualityStatus.MISSING);
        }
    }

    private static ProductionMarketDataProvider provider(String baseUrl, int attempts) {
        var properties = properties(baseUrl);
        var executor = new ProviderExecutionPolicy(CLOCK, ignored -> {}, attempts, Duration.ZERO, Duration.ZERO);
        var http =
                new ProviderHttpClient(HttpClient.newHttpClient(), executor, Duration.ofSeconds(2), new ObjectMapper());
        return new ProductionMarketDataProvider(http, properties, CLOCK);
    }

    private static ProviderProperties properties(String baseUrl) {
        return new ProviderProperties(
                new ProviderProperties.Market(ProviderProperties.MarketType.ALPHA_VANTAGE, baseUrl, "test-key"),
                new ProviderProperties.Fundamentals(
                        ProviderProperties.FundamentalsType.SEC, baseUrl, "SummitStock test@example.test"),
                new ProviderProperties.Execution(
                        2, Duration.ZERO, Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(2)));
    }

    private static ProviderMockServer.Response ok(String body) {
        return new ProviderMockServer.Response(200, body);
    }

    private static String dailyPayload() {
        return """
                {"Meta Data":{"3. Last Refreshed":"2026-08-04"},"Time Series (Daily)":{
                  "2026-08-04":{"1. open":"100","2. high":"104","3. low":"99","4. close":"102","5. adjusted close":"102","6. volume":"1000"},
                  "2026-08-03":{"1. open":"49","2. high":"51","3. low":"48","4. close":"50","5. adjusted close":"100","6. volume":"2000"}
                }}
                """;
    }

    private static String quotePayload() {
        return """
                {"Global Quote":{"01. symbol":"MSFT","05. price":"101.25","07. latest trading day":"2026-08-05"}}
                """;
    }

    private static String dividendPayload() {
        return """
                {"symbol":"MSFT","data":[{"ex_dividend_date":"2026-03-01","amount":"0.75"}]}
                """;
    }

    private static String splitPayload() {
        return """
                {"symbol":"MSFT","data":[{"effective_date":"2026-06-01","split_factor":"2.0"}]}
                """;
    }
}
