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

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
