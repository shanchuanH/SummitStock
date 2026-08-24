package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.earnings.AlphaVantageEarningsCalendarProvider;
import com.example.portfolio.estimates.AlphaVantageEstimateDataProvider;
import com.example.portfolio.estimates.EarningsEstimateResult;
import com.example.portfolio.macro.FredMacroDataProvider;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProductionSupplementalProviderContractTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T21:30:00Z"), ZoneOffset.UTC);

    @Test
    void normalizesEstimateCalendarAndMacroProviderPayloads() throws Exception {
        var calendarRequests = new AtomicInteger();
        var macroQuery = new AtomicReference<String>();
        try (var server = new ProviderMockServer(exchange -> {
            var query = exchange.getRequestURI().getRawQuery();
            if (query.contains("EARNINGS_ESTIMATES")) return ok(estimates());
            if (query.contains("EARNINGS_CALENDAR")) {
                calendarRequests.incrementAndGet();
                assertThat(query).doesNotContain("symbol=");
                return ok(calendar());
            }
            if (exchange.getRequestURI().getPath().contains("series/observations")) {
                macroQuery.set(query);
                return ok(macro());
            }
            return new ProviderMockServer.Response(404, "{}");
        })) {
            var properties = properties(server.baseUrl());
            var http = http();

            var estimates = new AlphaVantageEstimateDataProvider(http, properties, CLOCK).fetchEstimates("ibm");
            assertThat(estimates.quality()).isEqualTo(ProviderModels.QualityStatus.HEALTHY);
            assertThat(estimates.estimates())
                    .extracting(EarningsEstimateResult.Estimate::estimateType)
                    .containsExactly(
                            EarningsEstimateResult.EstimateType.EPS, EarningsEstimateResult.EstimateType.REVENUE);
            assertThat(estimates.estimates().getFirst().analystCount()).isEqualTo(24);

            var calendarProvider = new AlphaVantageEarningsCalendarProvider(http, properties, CLOCK);
            var calendar = calendarProvider.fetch("IBM", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-10-31"));
            assertThat(calendar.events()).hasSize(1);
            assertThat(calendar.events().getFirst().marketDate()).isEqualTo("2026-09-15");
            assertThat(calendarProvider
                            .fetch("MSFT", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-10-31"))
                            .events())
                    .hasSize(1);
            assertThat(calendarRequests).hasValue(1);

            var macro = new FredMacroDataProvider(http, properties, CLOCK)
                    .fetch("VIX3M", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-05"));
            assertThat(macro.seriesCode()).isEqualTo("VIX3M");
            assertThat(macroQuery.get()).contains("series_id=VXVCLS");
            assertThat(macro.observations()).hasSize(1);
            assertThat(macro.observations().getFirst().value()).isEqualByComparingTo("18.25");
        }
    }

    private static ProviderHttpClient http() {
        return new ProviderHttpClient(
                HttpClient.newHttpClient(),
                new ProviderExecutionPolicy(CLOCK, ignored -> {}, 1, Duration.ZERO, Duration.ZERO),
                Duration.ofSeconds(2),
                new ObjectMapper());
    }

    private static ProviderProperties properties(String baseUrl) {
        var execution = new ProviderProperties.Execution(
                1, Duration.ZERO, Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(2));
        return new ProviderProperties(
                new ProviderProperties.Market(ProviderProperties.MarketType.ALPHA_VANTAGE, baseUrl, "key"),
                new ProviderProperties.Fundamentals(
                        ProviderProperties.FundamentalsType.SEC, baseUrl, "SummitStock test@example.test"),
                new ProviderProperties.Estimates(
                        ProviderProperties.EstimatesType.ALPHA_VANTAGE,
                        baseUrl + "/query",
                        "key",
                        Duration.ZERO,
                        Duration.ofSeconds(2),
                        1,
                        Duration.ZERO),
                new ProviderProperties.EarningsCalendar(
                        ProviderProperties.EarningsCalendarType.ALPHA_VANTAGE,
                        baseUrl + "/query",
                        "key",
                        Duration.ZERO,
                        Duration.ofSeconds(2),
                        1,
                        Duration.ZERO),
                new ProviderProperties.Macro(
                        ProviderProperties.MacroType.FRED,
                        baseUrl + "/fred",
                        "key",
                        Duration.ZERO,
                        Duration.ofSeconds(2),
                        1,
                        Duration.ZERO),
                false,
                execution);
    }

    private static ProviderMockServer.Response ok(String body) {
        return new ProviderMockServer.Response(200, body);
    }

    private static String estimates() {
        return """
                {"symbol":"IBM","estimates":[{"date":"2026-12-31","horizon":"fiscal year",
                "eps_estimate_average":"12.32","eps_estimate_high":"12.63","eps_estimate_low":"12.08",
                "eps_estimate_analyst_count":"24.0000","revenue_estimate_average":"70375241150",
                "revenue_estimate_high":"70930000000","revenue_estimate_low":"69869318180",
                "revenue_estimate_analyst_count":"21.00"}]}
                """;
    }

    private static String calendar() {
        return """
                symbol,name,reportDate,fiscalDateEnding,estimate,currency
                IBM,International Business Machines,2026-09-15,2026-09-30,2.88,USD
                MSFT,Microsoft,2026-10-20,2026-09-30,3.12,USD
                """;
    }

    private static String macro() {
        return """
                {"observations":[{"date":"2026-08-04","value":"18.25"},{"date":"2026-08-05","value":"."}]}
                """;
    }
}
