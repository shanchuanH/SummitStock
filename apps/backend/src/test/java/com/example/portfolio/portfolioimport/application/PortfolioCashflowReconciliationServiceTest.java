package com.example.portfolio.portfolioimport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioCashflowReconciliationServiceTest {
    @Test
    void derivesInternalTradeValueFromQuantityDeltaWithoutUsingMarketGainAsCashflow() {
        var instrument = UUID.randomUUID();
        var before = new PortfolioCashflowReconciliationService.Snapshot(
                new BigDecimal("1000"),
                new BigDecimal("2000"),
                true,
                List.of(new PortfolioCashflowReconciliationService.PositionBalance(
                        instrument, new BigDecimal("10"), new BigDecimal("1000"))));
        var after = new PortfolioCashflowReconciliationService.Snapshot(
                new BigDecimal("800"),
                new BigDecimal("2000"),
                true,
                List.of(new PortfolioCashflowReconciliationService.PositionBalance(
                        instrument, new BigDecimal("12"), new BigDecimal("1200"))));

        var explanation = PortfolioCashflowReconciliationService.tradeExplanation(before, after);

        assertThat(explanation.quantityChanged()).isTrue();
        assertThat(explanation.netPurchaseValue()).isEqualByComparingTo("200");
        assertThat(after.brokerCash().subtract(before.brokerCash()).add(explanation.netPurchaseValue()))
                .isZero();
    }

    @Test
    void unchangedQuantityCannotAutoExplainAnExternalCashMovement() {
        var instrument = UUID.randomUUID();
        var position = new PortfolioCashflowReconciliationService.PositionBalance(
                instrument, new BigDecimal("10"), new BigDecimal("1100"));
        var before = new PortfolioCashflowReconciliationService.Snapshot(
                new BigDecimal("1000"), new BigDecimal("2000"), true, List.of(position));
        var after = new PortfolioCashflowReconciliationService.Snapshot(
                new BigDecimal("8000"), new BigDecimal("9100"), true, List.of(position));

        assertThat(PortfolioCashflowReconciliationService.tradeExplanation(before, after)
                        .quantityChanged())
                .isFalse();
    }
}
