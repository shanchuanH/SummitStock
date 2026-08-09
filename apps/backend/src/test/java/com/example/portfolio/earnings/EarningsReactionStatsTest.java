package com.example.portfolio.earnings;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class EarningsReactionStatsTest {
    @Test
    void summarizesTheMostRecentEightToTwelveEvents() {
        var reactions = IntStream.rangeClosed(1, 10)
                .mapToObj(value -> new EarningsReactionStats.Reaction(
                        new BigDecimal(value).movePointLeft(2),
                        new BigDecimal(value - 6).movePointLeft(2),
                        new BigDecimal(value).movePointLeft(3)))
                .toList();

        var result = new EarningsReactionStats().summarize(reactions);

        assertThat(result.ready()).isTrue();
        assertThat(result.eventCount()).isEqualTo(10);
        assertThat(result.medianAbsMove()).isEqualByComparingTo("0.05");
        assertThat(result.p75AbsMove()).isEqualByComparingTo("0.08");
        assertThat(result.p90AbsMove()).isEqualByComparingTo("0.09");
        assertThat(result.worstDownsideGap()).isEqualByComparingTo("-0.05");
        assertThat(result.bestUpsideGap()).isEqualByComparingTo("0.04");
    }
}
