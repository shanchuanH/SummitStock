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

class YahooMarketDataProviderContractTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-04T21:30:00Z"), ZoneOffset.UTC);

    @Test
    void normalizesAdjustedBarsQuoteAndActionsAndReusesOneChartPayload() throws Exception {
        var requests = new AtomicInteger();
        try (var server = new ProviderMockServer(exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestURI().getPath()).endsWith("/MSFT");
            assertThat(exchange.getRequestURI().getRawQuery())
                    .contains("interval=1d", "events=div%2Csplits", "includeAdjustedClose=true");
            return ok(chartPayload());
        })) {
            var provider = provider(server.baseUrl() + "/v8/finance/chart", 1);
            var quote = provider.fetchQuote("msft");
            assertThat(quote.symbol()).isEqualTo("MSFT");
            assertThat(quote.last()).isEqualByComparingTo("102.50");
            assertThat(quote.decisionPrice().marketDate()).isEqualTo("2026-08-04");
            assertThat(quote.decisionPrice().quality()).isEqualTo(ProviderModels.QualityStatus.HEALTHY);
            assertThat(quote.executionLiquidity().quality()).isEqualTo(ProviderModels.QualityStatus.MISSING);
            assertThat(quote.provenance().provider()).isEqualTo("yahoo-finance-chart");
            assertThat(quote.provenance().warnings()).contains("UNOFFICIAL_YAHOO_FINANCE_CHART_ENDPOINT");

            var bars = provider.fetchDailyBars("MSFT", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-05"));
            assertThat(bars.bars()).hasSize(2);
            assertThat(bars.bars()).allMatch(ProviderModels.DailyBar::adjusted);
            assertThat(bars.bars().getFirst().close()).isEqualByComparingTo("100");
            assertThat(bars.bars().getFirst().open()).isEqualByComparingTo("98");
            assertThat(bars.provenance().qualityStatus()).isEqualTo(ProviderModels.QualityStatus.HEALTHY);

            var actions = provider.fetchCorporateActions(
                    "MSFT", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-08-05"));
            assertThat(actions.actions())
                    .extracting(ProviderModels.CorporateAction::type)
                    .containsExactly("DIVIDEND", "SPLIT");
            assertThat(actions.actions().getLast().ratio()).isEqualByComparingTo("2");
            assertThat(requests).hasValue(1);
        }
    }

    @Test
    void rejectsYahooErrorsAndIncompleteAdjustedEvidence() throws Exception {
        try (var server = new ProviderMockServer(exchange ->
                ok("{\"chart\":{\"result\":null,\"error\":{\"code\":\"Not Found\",\"description\":\"missing\"}}}"))) {
            assertThatThrownBy(() -> provider(server.baseUrl() + "/chart", 1).fetchQuote("BAD"))
                    .isInstanceOf(ProviderCallException.class)
                    .extracting(error -> ((ProviderCallException) error).code())
                    .isEqualTo("PROVIDER_UNAVAILABLE");
        }
        try (var server = new ProviderMockServer(
                exchange -> ok(chartPayload().replace("\"adjclose\":[{\"adjclose\":[100,102]}]", "\"adjclose\":[]")))) {
            assertThatThrownBy(() -> provider(server.baseUrl() + "/chart", 1)
                            .fetchDailyBars("MSFT", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-05")))
                    .isInstanceOf(ProviderCallException.class)
                    .extracting(error -> ((ProviderCallException) error).code())
                    .isEqualTo("PROVIDER_MALFORMED");
        }
    }

    private static YahooMarketDataProvider provider(String baseUrl, int attempts) {
        var properties = new ProviderProperties(
                new ProviderProperties.Market(ProviderProperties.MarketType.YAHOO, baseUrl, ""),
                new ProviderProperties.Fundamentals(
                        ProviderProperties.FundamentalsType.SEC, baseUrl, "SummitStock test@example.test"),
                new ProviderProperties.Execution(
                        attempts, Duration.ZERO, Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(2)));
        var executor = new ProviderExecutionPolicy(CLOCK, ignored -> {}, attempts, Duration.ZERO, Duration.ZERO);
        var http =
                new ProviderHttpClient(HttpClient.newHttpClient(), executor, Duration.ofSeconds(2), new ObjectMapper());
        return new YahooMarketDataProvider(http, properties, CLOCK);
    }

    private static ProviderMockServer.Response ok(String body) {
        return new ProviderMockServer.Response(200, body);
    }

    private static String chartPayload() {
        return """
                {"chart":{"result":[{
                  "meta":{"currency":"USD","symbol":"MSFT","regularMarketPrice":102.5},
                  "timestamp":[1785763800,1785850200],
                  "events":{
                    "dividends":{"1772375400":{"amount":0.75,"date":1772375400}},
                    "splits":{"1780320600":{"date":1780320600,"numerator":2,"denominator":1,"splitRatio":"2:1"}}
                  },
                  "indicators":{
                    "quote":[{"open":[49,101],"high":[51,104],"low":[48,100],"close":[50,102],"volume":[2000,1000]}],
                    "adjclose":[{"adjclose":[100,102]}]
                  }
                }],"error":null}}
                """;
    }
}
