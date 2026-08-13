package com.example.portfolio.analysis.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TrueDrawdownAttributionTest {
    @Test
    void attributionRanksActualLossAndUsesNavDrawdownLossAsItsDenominator() {
        var result = DrawdownAttributionService.summarize(
                List.of(
                        new DrawdownAttributionService.LossRow("SMALL_NOW_BIG_LOSS", bd("9000")),
                        new DrawdownAttributionService.LossRow("LARGEST_NOW_SMALL_LOSS", bd("1000"))),
                List.of(new DrawdownAttributionService.LossRow("TECH", bd("7500"))),
                bd("10000"));

        assertThat(result.largestPositionLossShare()).isEqualTo(0.9);
        assertThat(result.largestClusterLossShare()).isEqualTo(1.0);
        assertThat(result.positionJson()).contains("SMALL_NOW_BIG_LOSS", "0.9");
        assertThat(result.positionJson()).contains("LARGEST_NOW_SMALL_LOSS", "0.1");
        assertThat(result.clusterJson()).contains("TECH", "0.75");
    }

    @Test
    void postPeakBuysAndSellsUseExecutionCashflowsInsteadOfCurrentQuantityShortcut() {
        var added = DrawdownAttributionService.loss(equation("100", "100", "150", "70", "50", "4000", 0));
        var sold = DrawdownAttributionService.loss(equation("100", "100", "40", "80", "-60", "-5400", 0));
        var unknownExecution = DrawdownAttributionService.loss(equation("100", "100", "40", "80", "-60", "0", 1));

        assertThat(added)
                .get()
                .extracting(DrawdownAttributionService.LossRow::lossAmount)
                .isEqualTo(bd("3500"));
        assertThat(sold)
                .get()
                .extracting(DrawdownAttributionService.LossRow::lossAmount)
                .isEqualTo(bd("1400"));
        assertThat(unknownExecution).isEmpty();
    }

    private static DrawdownAttributionService.LossEquation equation(
            String peakQuantity,
            String peakPrice,
            String currentQuantity,
            String currentPrice,
            String quantityDelta,
            String capitalFlow,
            long incompleteRows) {
        return new DrawdownAttributionService.LossEquation(
                UUID.randomUUID(),
                "TEST",
                bd(peakQuantity),
                bd(peakPrice),
                bd(currentQuantity),
                bd(currentPrice),
                bd(quantityDelta),
                bd(capitalFlow),
                incompleteRows);
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
