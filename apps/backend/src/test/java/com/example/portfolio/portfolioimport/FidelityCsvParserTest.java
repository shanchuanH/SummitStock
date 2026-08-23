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
    void recognizesCommonFidelityEquityDescriptionsAndSkipsExportDisclaimers() {
        var csv =
                "Account number,Account name,Symbol,Description,Quantity,Last price,Current value,Cost basis total,Average cost basis,Type\n"
                        + "Z12345678,Individual,SPAXX**,HELD IN MONEY MARKET,,,$21000,,,Cash,\n"
                        + "Z12345678,Individual,AAOI,APPLIED OPTOELECTRONICS INC,57,$124.82,$7114.74,$8844.63,$155.17,Cash,\n"
                        + "Z12345678,Individual,DXYZ,DESTINY TECH100 INC COM SHS,250,$34.43,$8607.50,$12581.66,$50.33,Cash,\n"
                        + "Z12345678,Individual,GOOGL,ALPHABET INC CAP STK CL A,23,$344.82,$7930.86,$8002.84,$347.95,Cash,\n"
                        + "Z12345678,Individual,MSFT,MICROSOFT CORP,15,$483.24,$7248.60,$6016.90,$401.13,Cash,\n"
                        + "Z12345678,Individual,NOK,NOKIA OYJ ADR EACH REPR 1 ORD NPV,750,$10.21,$7657.50,$10741.13,$14.32,Cash,\n"
                        + "Z12345678,Individual,NVDA,NVIDIA CORPORATION COM,6,$214.72,$1288.32,$1183.98,$197.33,Cash,\n"
                        + "\"The data and information in this spreadsheet is provided to you solely for your use.\"\n"
                        + "\"Brokerage services are provided by Fidelity Brokerage Services LLC (FBS).\"\n"
                        + "\"Date downloaded Aug-23-2026 5:23 p.m ET\"\n";

        var preview = parser.parse(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "positions.csv");

        assertThat(preview.summary().rowCount()).isEqualTo(7);
        assertThat(preview.summary().validRowCount()).isEqualTo(7);
        assertThat(preview.summary().errorRowCount()).isZero();
        assertThat(preview.cash()).singleElement().satisfies(row -> {
            assertThat(row.symbol()).isEqualTo("SPAXX");
            assertThat(row.currentValue()).isEqualByComparingTo("21000");
        });
        assertThat(preview.holdings())
                .extracting(row -> row.symbol(), row -> row.assetType(), row -> row.status())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("AAOI", "EQUITY", ImportRowStatus.VALID),
                        org.assertj.core.groups.Tuple.tuple("DXYZ", "EQUITY", ImportRowStatus.VALID),
                        org.assertj.core.groups.Tuple.tuple("GOOGL", "EQUITY", ImportRowStatus.VALID),
                        org.assertj.core.groups.Tuple.tuple("MSFT", "EQUITY", ImportRowStatus.VALID),
                        org.assertj.core.groups.Tuple.tuple("NOK", "EQUITY", ImportRowStatus.VALID),
                        org.assertj.core.groups.Tuple.tuple("NVDA", "EQUITY", ImportRowStatus.VALID));
        assertThat(preview.errors()).isEmpty();
    }

    private static byte[] fixture(String name) throws IOException {
        try (var input = FidelityCsvParserTest.class.getResourceAsStream("/portfolio-import/" + name)) {
            if (input == null) throw new IOException("Missing test fixture " + name);
            return input.readAllBytes();
        }
    }
}
