package com.example.portfolio.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PerformanceMathTest {
    @Test
    void navReturnIsCashflowAdjustedBecauseItUsesPortfolioUnits() {
        var result = PerformanceMath.returnBetween(List.of(point(1, "1.00"), point(2, "1.10"), point(3, "1.21")));
        assertThat(result).isEqualByComparingTo("0.21");
    }

    @Test
    void contributionUsesPerShareReturnSoQuantityChangesAreNotCalledPerformance() {
        var start = new PerformanceMath.PositionPoint(date(1), new BigDecimal("10"), new BigDecimal("1000"));
        var end = new PerformanceMath.PositionPoint(date(2), new BigDecimal("20"), new BigDecimal("2400"));
        assertThat(PerformanceMath.contribution(start, end, new BigDecimal("10000")))
                .isEqualByComparingTo("0.02");
    }

    @Test
    void calculatesPeakToTroughDrawdownAndRejectsInsufficientSeries() {
        assertThat(PerformanceMath.maxDrawdown(List.of(point(1, "1.0"), point(2, "1.2"), point(3, "0.9"))))
                .isEqualByComparingTo("0.25");
        assertThat(PerformanceMath.returnBetween(List.of(point(1, "1.0")))).isNull();
    }

    private static PerformanceMath.DatedValue point(int day, String value) {
        return new PerformanceMath.DatedValue(date(day), new BigDecimal(value));
    }

    private static LocalDate date(int day) {
        return LocalDate.of(2026, 1, day);
    }
}
