package com.example.portfolio.analysis.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrueDrawdownAttributionTest {
    @Test
    void attributionRanksActualPeakToCurrentLossNotCurrentPositionSize() {
        var result = DrawdownAttributionService.summarize(
                List.of(
                        new DrawdownAttributionService.LossRow("SMALL_NOW_BIG_LOSS", bd("9000")),
                        new DrawdownAttributionService.LossRow("LARGEST_NOW_SMALL_LOSS", bd("1000"))),
                List.of(new DrawdownAttributionService.LossRow("TECH", bd("7500"))),
                bd("100000"));

        assertThat(result.largestPositionLossShare()).isEqualTo(0.9);
        assertThat(result.largestClusterLossShare()).isEqualTo(1.0);
        assertThat(result.positionJson()).contains("SMALL_NOW_BIG_LOSS", "-0.09");
        assertThat(result.positionJson()).contains("LARGEST_NOW_SMALL_LOSS", "-0.01");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
