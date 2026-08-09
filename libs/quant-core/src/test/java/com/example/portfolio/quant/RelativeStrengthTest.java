package com.example.portfolio.quant;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class RelativeStrengthTest {
    @Test
    void comparesAlignedTotalPriceChangeAgainstBenchmark() {
        var start = LocalDate.of(2026, 1, 1);
        var instrument = IntStream.rangeClosed(0, 63)
                .mapToObj(day -> bar(start.plusDays(day), 100 + day * 2))
                .toList();
        var benchmark = IntStream.rangeClosed(0, 63)
                .mapToObj(day -> bar(start.plusDays(day), 100 + day))
                .toList();

        var result = Indicators.relativeStrength(instrument, benchmark, 63);

        assertThat(result.value()).isPresent();
        assertThat(result.value().orElseThrow()).isGreaterThan(1.0);
    }

    private static QuantBar bar(LocalDate date, int close) {
        var value = BigDecimal.valueOf(close);
        return new QuantBar(date, value, value, value, value, BigDecimal.valueOf(1000), true);
    }
}
