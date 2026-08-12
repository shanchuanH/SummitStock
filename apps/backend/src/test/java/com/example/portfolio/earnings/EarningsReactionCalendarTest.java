package com.example.portfolio.earnings;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.market.provider.UsEquityTradingCalendar;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class EarningsReactionCalendarTest {
    @Test
    void afterCloseReactionUsesNextActualSessionAcrossGoodFriday() {
        var calculator = new EarningsReactionCalculator(new UsEquityTradingCalendar());
        var bars = List.of(
                bar("2026-04-01", "99", "100"),
                bar("2026-04-02", "100", "101"),
                bar("2026-04-03", "999", "999"),
                bar("2026-04-06", "110", "112"));

        var result =
                calculator.calculate(LocalDate.parse("2026-04-02"), EarningsCalendarProvider.Timing.AFTER_CLOSE, bars);

        assertThat(result.preClose()).isEqualByComparingTo("101");
        assertThat(result.nextOpen()).isEqualByComparingTo("110");
        assertThat(result.nextClose()).isEqualByComparingTo("112");
    }

    private static EarningsReactionCalculator.Bar bar(String date, String open, String close) {
        return new EarningsReactionCalculator.Bar(
                LocalDate.parse(date), new BigDecimal(open), new BigDecimal(close), BigDecimal.valueOf(1000));
    }
}
