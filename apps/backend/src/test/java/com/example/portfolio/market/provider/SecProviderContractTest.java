package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SecProviderContractTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T16:00:00Z"), ZoneOffset.UTC);

    @Test
    void readsSubmissionsAndExplicitCompanyFactMappingsWithSecIdentity() throws Exception {
        var userAgent = new AtomicReference<String>();
        try (var server = new ProviderMockServer(exchange -> {
            userAgent.set(exchange.getRequestHeaders().getFirst("User-Agent"));
            return exchange.getRequestURI().getPath().startsWith("/submissions/")
                    ? ok(submissions())
                    : ok(companyFacts());
        })) {
            var provider = provider(server.baseUrl());
            var filings = provider.fetchFilings("789019");
            assertThat(filings.cik()).isEqualTo("0000789019");
            assertThat(filings.filings()).singleElement().satisfies(filing -> {
                assertThat(filing.accessionNumber()).isEqualTo("0000789019-26-000001");
                assertThat(filing.periodEnd()).hasToString("2026-06-30");
                assertThat(filing.form()).isEqualTo("10-Q");
                assertThat(filing.sourceUrl()).contains("/Archives/edgar/data/789019/");
            });
            assertThat(filings.provenance().checksum()).hasSize(64);

            var facts = provider.fetchCompanyFacts("0000789019");
            Set<String> metrics = facts.facts().stream()
                    .map(ProviderModels.CompanyFact::businessMetric)
                    .collect(Collectors.toSet());
            assertThat(metrics)
                    .containsExactlyInAnyOrder(
                            "Revenue",
                            "OperatingIncome",
                            "NetIncome",
                            "CashAndCashEquivalents",
                            "LongTermDebt",
                            "ShareholdersEquity",
                            "OperatingCashFlow",
                            "CapitalExpenditures",
                            "DilutedEPS",
                            "SharesDiluted");
            assertThat(facts.facts()).allSatisfy(fact -> {
                assertThat(fact.concept()).isNotBlank();
                assertThat(fact.unit()).isNotBlank();
                assertThat(fact.accessionNumber()).isEqualTo("0000789019-26-000001");
                assertThat(fact.sourceUri()).contains("sec.gov/Archives/edgar");
            });
            assertThat(facts.provenance().qualityStatus()).isEqualTo(ProviderModels.QualityStatus.PARTIAL);
            assertThat(facts.provenance().warnings())
                    .contains(
                            "MISSING_METRIC:GrossProfit",
                            "MISSING_METRIC:CurrentAssets",
                            "MISSING_METRIC:CurrentLiabilities");
            assertThat(userAgent).hasValue("SummitStock test@example.test");
        }
    }

    @Test
    void emptyCompanyFactsAreMissing() throws Exception {
        try (var server = new ProviderMockServer(exchange -> ok("{\"facts\":{\"us-gaap\":{}}}"))) {
            var result = provider(server.baseUrl()).fetchCompanyFacts("789019");
            assertThat(result.facts()).isEmpty();
            assertThat(result.provenance().qualityStatus()).isEqualTo(ProviderModels.QualityStatus.MISSING);
        }
    }

    private static SecFundamentalsProvider provider(String baseUrl) {
        var properties = new ProviderProperties(
                new ProviderProperties.Market(ProviderProperties.MarketType.ALPHA_VANTAGE, baseUrl, "test-key"),
                new ProviderProperties.Fundamentals(
                        ProviderProperties.FundamentalsType.SEC, baseUrl, "SummitStock test@example.test"),
                new ProviderProperties.Execution(
                        1, Duration.ZERO, Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(2)));
        var executor = new ProviderExecutor(CLOCK, ignored -> {}, 1, Duration.ZERO, Duration.ZERO);
        var http =
                new ProviderHttpClient(HttpClient.newHttpClient(), executor, Duration.ofSeconds(2), new ObjectMapper());
        return new SecFundamentalsProvider(http, properties, CLOCK);
    }

    private static ProviderMockServer.Response ok(String body) {
        return new ProviderMockServer.Response(200, body);
    }

    private static String submissions() {
        return """
                {"filings":{"recent":{
                  "accessionNumber":["0000789019-26-000001"],
                  "filingDate":["2026-08-01"],
                  "reportDate":["2026-06-30"],
                  "form":["10-Q"],
                  "primaryDocument":["msft-20260630.htm"]
                }}}
                """;
    }

    private static String companyFacts() {
        return "{\"facts\":{\"us-gaap\":{"
                + String.join(
                        ",",
                        concept("RevenueFromContractWithCustomerExcludingAssessedTax", "USD", "1000"),
                        concept("OperatingIncomeLoss", "USD", "200"),
                        concept("NetIncomeLoss", "USD", "150"),
                        concept("CashAndCashEquivalentsAtCarryingValue", "USD", "300"),
                        concept("LongTermDebtNoncurrent", "USD", "400"),
                        concept("StockholdersEquity", "USD", "500"),
                        concept("NetCashProvidedByUsedInOperatingActivities", "USD", "250"),
                        concept("PaymentsToAcquirePropertyPlantAndEquipment", "USD", "50"),
                        concept("EarningsPerShareDiluted", "USD/shares", "2.5"),
                        concept("WeightedAverageNumberOfDilutedSharesOutstanding", "shares", "100"))
                + "}}}";
    }

    private static String concept(String name, String unit, String value) {
        return "\"" + name + "\":{\"units\":{\"" + unit + "\":[{"
                + "\"val\":" + value + ",\"start\":\"2026-04-01\",\"end\":\"2026-06-30\","
                + "\"filed\":\"2026-08-01\",\"accn\":\"0000789019-26-000001\",\"form\":\"10-Q\"}]}}";
    }
}
