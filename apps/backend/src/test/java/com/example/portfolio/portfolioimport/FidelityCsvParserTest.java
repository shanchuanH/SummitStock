package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.portfolioimport.domain.ImportRowStatus;
import com.example.portfolio.portfolioimport.infrastructure.FidelityCsvParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class FidelityCsvParserTest {
    private final FidelityCsvParser parser = new FidelityCsvParser(
            new ObjectMapper(), Clock.fixed(Instant.parse("2026-08-05T16:00:00Z"), ZoneOffset.UTC));

    @Test
    void parsesFidelityPositionsWithoutLosingFractionalCashOrCompensationSemantics() throws IOException {
        var preview = parser.parse(fixture("fidelity-positions.csv"), "fidelity-positions.csv");

        assertThat(preview.summary().rowCount()).isEqualTo(6);
        assertThat(preview.summary().validRowCount()).isEqualTo(5);
        assertThat(preview.summary().errorRowCount()).isEqualTo(1);
        assertThat(preview.summary().estimatedInvestedValue()).isEqualByComparingTo("6425.05");
        assertThat(preview.summary().estimatedCashValue()).isEqualByComparingTo("14000");
        assertThat(preview.accounts())
                .extracting(account -> account.accountNumberMasked())
                .containsExactly("***5678", "***4321");

        var fractional = preview.holdings().stream()
                .filter(row -> "AMZN".equals(row.symbol()) && "***5678".equals(row.accountNumberMasked()))
                .findFirst()
                .orElseThrow();
        assertThat(fractional.quantity()).isEqualByComparingTo("3.125");
        assertThat(fractional.averageCost()).isNull();
        assertThat(fractional.costBasis()).isNull();
        assertThat(fractional.status()).isEqualTo(ImportRowStatus.WARNING);
        assertThat(fractional.warnings()).containsExactly("COST_BASIS_MISSING");

        assertThat(preview.cash()).singleElement().satisfies(row -> {
            assertThat(row.symbol()).isEqualTo("SPAXX");
            assertThat(row.currentValue()).isEqualByComparingTo("14000");
        });
        assertThat(preview.holdings())
                .filteredOn(row -> "UNVESTED_COMPENSATION".equals(row.rowType()))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.assetType()).isEqualTo("COMPENSATION");
                    assertThat(row.currentValue()).isEqualByComparingTo("4000");
                });
        assertThat(preview.errors()).containsExactly("Row 7 could not be classified or is missing required values.");
        assertThat(preview.dataAsOf()).isEqualTo(Instant.parse("2026-08-05T16:00:00Z"));
    }

    @Test
    void normalizesQuotedCurrencyAndAccountingNegatives() throws IOException {
        var csv = "Account Number,Account Name,Symbol,Description,Quantity,Last Price,Current Value,Type\n"
                + "Z12345678,Primary,SPY,SPDR ETF,1,\"$1,234.50\",\"($1,234.50)\",ETF\n";

        var preview = parser.parse(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "negative.csv");

        assertThat(preview.holdings()).singleElement().satisfies(row -> {
            assertThat(row.lastPrice()).isEqualByComparingTo(new BigDecimal("1234.50"));
            assertThat(row.currentValue()).isEqualByComparingTo(new BigDecimal("-1234.50"));
            assertThat(row.status()).isEqualTo(ImportRowStatus.ERROR);
            assertThat(row.rawJson()).doesNotContain("Z12345678").contains("***5678");
        });
        assertThat(preview.summary().estimatedInvestedValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parsesCurrentFidelityCashSettlementRowsAndIgnoresExportMetadata() {
        var csv =
                """
                Account number,Account name,Symbol,Description,Quantity,Last price,Last price change,Current value,Today's gain/loss dollar,Today's gain/loss percent,Total gain/loss dollar,Total gain/loss percent,Percent of account,Cost basis total,Average cost basis,Type
                Z12345678,Individual,SPAXX**,HELD IN MONEY MARKET,,,,$22156.01,,,,,20.05%,,,Cash,
                Z12345678,Individual,AAOI,APPLIED OPTOELECTRONICS INC,50,$150.28,+$20.20,$7514.00,+$1010.00,+15.52%,-$395.64,-5.01%,6.80%,$7909.64,$158.19,Cash,
                Z12345678,Individual,QQQM,INVESCO EXCH TRADED FD TR II NASDAQ 100 ETF,37.775,$301.01,+$0.39,$11370.65,+$91.41,+$0.68%,+$1203.96,+9.84%,12.16%,$10166.69,$269.14,Cash,

                "The data and information in this spreadsheet is provided for informational purposes only."

                "Brokerage services are provided by Fidelity Brokerage Services LLC."

                "Date downloaded Aug-17-2026 12:45 a.m ET"
                """;

        var preview = parser.parse(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "current-fidelity.csv");

        assertThat(preview.summary().rowCount()).isEqualTo(3);
        assertThat(preview.summary().errorRowCount()).isZero();
        assertThat(preview.errors()).isEmpty();
        assertThat(preview.summary().estimatedCashValue()).isEqualByComparingTo("22156.01");
        assertThat(preview.cash()).singleElement().satisfies(row -> {
            assertThat(row.symbol()).isEqualTo("SPAXX");
            assertThat(row.status()).isEqualTo(ImportRowStatus.VALID);
        });
        assertThat(preview.holdings())
                .extracting(row -> row.symbol() + ":" + row.assetType() + ":" + row.status())
                .containsExactly("AAOI:EQUITY:VALID", "QQQM:ETF:VALID");
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = FidelityCsvParserTest.class.getResourceAsStream("/portfolio-import/" + name)) {
            if (input == null) throw new IOException("Missing test fixture " + name);
            return input.readAllBytes();
        }
    }
}
