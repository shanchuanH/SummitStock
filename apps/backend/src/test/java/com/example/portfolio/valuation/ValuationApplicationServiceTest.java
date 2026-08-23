package com.example.portfolio.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ValuationApplicationServiceTest {
    @Test
    void negativeTtmEarningsMakePeNotMeaningfulInsteadOfCheap() {
        var input = new ValuationEvidenceStore.InputRow(
                UUID.randomUUID(),
                "LOSS",
                LocalDate.parse("2026-08-12"),
                bd("20"),
                bd("-2"),
                bd("1"),
                bd("100"),
                bd("10"),
                bd("20"),
                bd("5"),
                bd("10"),
                bd("0.1"),
                "HEALTHY",
                "POSITIVE");

        var metrics = ValuationApplicationService.metrics(input);

        assertThat(metrics.trailingPe()).isNull();
        assertThat(metrics.forwardPe()).isEqualByComparingTo("20");
        assertThat(metrics.priceSales()).isEqualByComparingTo("2");
    }

    @Test
    void marketCapAndEnterpriseMultiplesUsePointInTimeCommonShares() {
        var input = new ValuationEvidenceStore.InputRow(
                UUID.randomUUID(),
                "BASIS",
                LocalDate.parse("2026-08-12"),
                bd("20"),
                bd("2"),
                bd("2.5"),
                bd("1000"),
                bd("100"),
                bd("50"),
                bd("150"),
                bd("98"),
                bd("0.1"),
                "HEALTHY",
                "POSITIVE");

        var metrics = ValuationApplicationService.metrics(input);

        assertThat(metrics.marketCap()).isEqualByComparingTo("1960");
        assertThat(metrics.priceSales()).isEqualByComparingTo("1.96");
        assertThat(metrics.evSales()).isEqualByComparingTo("2.06");
        assertThat(metrics.fcfYield()).isEqualByComparingTo("0.05102040816326530612244897959183673");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
