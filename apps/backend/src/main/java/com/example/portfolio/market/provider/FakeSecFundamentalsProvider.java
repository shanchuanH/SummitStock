package com.example.portfolio.market.provider;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local-fixture", "test"})
public class FakeSecFundamentalsProvider implements FundamentalsProvider {
    private final Clock clock = Clock.systemUTC();
    private final boolean completeFixtures;

    public FakeSecFundamentalsProvider() {
        this(false);
    }

    @Autowired
    public FakeSecFundamentalsProvider(
            @Value("${portfolio.test.complete-provider-fixtures:false}") boolean completeFixtures) {
        this.completeFixtures = completeFixtures;
    }

    @Override
    public String providerId() {
        return "fake-sec-ir";
    }

    @Override
    public ProviderModels.FilingIndexResult fetchFilings(String cik) {
        if (!completeFixtures) return new ProviderModels.FilingIndexResult(cik, List.of(), provenance(cik));
        var latest = latestPeriodEnd();
        return new ProviderModels.FilingIndexResult(
                cik, List.of(filing(cik, latest.minusYears(1)), filing(cik, latest)), provenance(cik + "-filings"));
    }

    @Override
    public ProviderModels.CompanyFactsResult fetchCompanyFacts(String cik) {
        if (!completeFixtures) return new ProviderModels.CompanyFactsResult(cik, List.of(), provenance(cik));
        var latest = latestPeriodEnd();
        var facts = new ArrayList<ProviderModels.CompanyFact>();
        addAnnualFacts(facts, cik, latest.minusYears(1), false);
        addAnnualFacts(facts, cik, latest, true);
        return new ProviderModels.CompanyFactsResult(cik, facts, provenance(cik + "-facts"));
    }

    private LocalDate latestPeriodEnd() {
        return LocalDate.now(clock).minusMonths(6);
    }

    private static ProviderModels.Filing filing(String cik, LocalDate end) {
        var accession = "fixture-" + cik + "-" + end.getYear();
        return new ProviderModels.Filing(
                accession, "10-K", end.plusMonths(2), end, "https://fixture.sec/" + accession, "annual.htm");
    }

    private static void addAnnualFacts(
            List<ProviderModels.CompanyFact> facts, String cik, LocalDate end, boolean latest) {
        var values = List.of(
                metric("REVENUE", "RevenueFromContractWithCustomerExcludingAssessedTax", latest ? "1200" : "1000"),
                metric("GROSS_PROFIT", "GrossProfit", latest ? "600" : "480"),
                metric("OPERATING_INCOME", "OperatingIncomeLoss", latest ? "300" : "220"),
                metric("NET_INCOME", "NetIncomeLoss", latest ? "240" : "180"),
                metric("DILUTED_EPS", "EarningsPerShareDiluted", latest ? "12" : "9"),
                metric("OPERATING_CASH_FLOW", "NetCashProvidedByUsedInOperatingActivities", latest ? "340" : "280"),
                metric("CAPEX", "PaymentsToAcquirePropertyPlantAndEquipment", latest ? "60" : "55"),
                metric("CASH", "CashAndCashEquivalentsAtCarryingValue", latest ? "500" : "420"),
                metric("TOTAL_DEBT", "LongTermDebtNoncurrent", latest ? "100" : "120"),
                metric("CURRENT_ASSETS", "AssetsCurrent", latest ? "800" : "700"),
                metric("CURRENT_LIABILITIES", "LiabilitiesCurrent", latest ? "300" : "290"),
                metric("SHAREHOLDERS_EQUITY", "StockholdersEquity", latest ? "1400" : "1200"),
                metric("DILUTED_SHARES", "WeightedAverageNumberOfDilutedSharesOutstanding", latest ? "100" : "100"));
        var accession = "fixture-" + cik + "-" + end.getYear();
        for (var value : values) {
            facts.add(new ProviderModels.CompanyFact(
                    value.businessMetric(),
                    "us-gaap",
                    value.concept(),
                    value.businessMetric().equals("DILUTED_EPS") ? "USD/shares" : "USD",
                    value.value(),
                    end.minusYears(1),
                    end,
                    end.plusMonths(2),
                    accession,
                    "10-K",
                    "https://fixture.sec/" + accession,
                    end.getYear(),
                    "FY"));
        }
    }

    private static FixtureMetric metric(String businessMetric, String concept, String value) {
        return new FixtureMetric(businessMetric, concept, new BigDecimal(value));
    }

    private ProviderModels.Provenance provenance(String checksum) {
        var now = clock.instant();
        return new ProviderModels.Provenance(
                "fake-sec-ir",
                now,
                now,
                String.format("%064x", checksum.hashCode()),
                "v1",
                completeFixtures ? ProviderModels.QualityStatus.HEALTHY : ProviderModels.QualityStatus.MISSING,
                completeFixtures ? List.of() : List.of("FIXTURE_HAS_NO_FUNDAMENTALS"));
    }

    private record FixtureMetric(String businessMetric, String concept, BigDecimal value) {}
}
