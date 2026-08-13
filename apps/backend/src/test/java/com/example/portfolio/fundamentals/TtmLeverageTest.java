package com.example.portfolio.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class TtmLeverageTest {
    @Test
    void netDebtToFcfUsesExactlyFourQuarterlyCashFlows() {
        var values = new EnumMap<FinancialMetric, BigDecimal>(FinancialMetric.class);
        values.put(FinancialMetric.NET_CASH, bd("-120"));

        FinancialHealthApplicationService.addTtmLeverage(values, quarters(4));

        assertThat(values.get(FinancialMetric.NET_DEBT_TO_FCF)).isEqualByComparingTo("3");
    }

    @Test
    void annualOrIncompleteQuarterlyFcfCannotMasqueradeAsTtm() {
        var values = new EnumMap<FinancialMetric, BigDecimal>(FinancialMetric.class);
        values.put(FinancialMetric.NET_CASH, bd("-120"));
        var incomplete = new ArrayList<>(quarters(3));
        incomplete.add(period("ANNUAL", "2025-12-31", "100"));

        FinancialHealthApplicationService.addTtmLeverage(values, incomplete);

        assertThat(values).doesNotContainKey(FinancialMetric.NET_DEBT_TO_FCF);
    }

    private static List<FinancialEvidenceStore.MetricPeriod> quarters(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> period(
                        "QUARTERLY",
                        LocalDate.parse("2026-06-30").minusMonths(index * 3L).toString(),
                        "10"))
                .toList();
    }

    private static FinancialEvidenceStore.MetricPeriod period(String type, String date, String value) {
        var end = LocalDate.parse(date);
        return new FinancialEvidenceStore.MetricPeriod(
                UUID.randomUUID(),
                end,
                end.plusDays(30),
                type,
                FinancialMetric.FREE_CASH_FLOW.name(),
                bd(value),
                "HEALTHY");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
