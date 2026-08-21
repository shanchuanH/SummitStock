package com.example.portfolio.portfolioimport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PortfolioCashflowReconciliationServiceTest {
    @Test
    void strategyBucketReallocationCannotCreateAReportedBrokerCashflow() {
        var positions = List.<PortfolioCashflowReconciliationService.PositionBalance>of();
        var before = snapshot("30000", "30000", positions);
        var after = snapshot("30000", "30000", positions);

        var result = PortfolioCashflowReconciliationService.assess(before, after);

        assertThat(result.status()).isEqualTo("NO_CASH_CHANGE");
        assertThat(result.cashChange()).isZero();
    }

    @Test
    void rawBrokerCashIncreaseWithoutPositionChangeRequiresReconciliation() {
        var positions = List.<PortfolioCashflowReconciliationService.PositionBalance>of();

        var result = PortfolioCashflowReconciliationService.assess(
                snapshot("30000", "30000", positions), snapshot("35000", "35000", positions));

        assertThat(result.status()).isEqualTo("REQUIRED");
        assertThat(result.cashChange()).isEqualByComparingTo("5000");
    }

    @Test
    void cashDecreaseMatchedByPositionPurchaseIsAnInternalTrade() {
        var instrument = UUID.randomUUID();
        var beforePositions = List.of(position(instrument, "10", "10000"));
        var afterPositions = List.of(position(instrument, "20", "20000"));

        var result = PortfolioCashflowReconciliationService.assess(
                snapshot("30000", "40000", beforePositions), snapshot("20000", "40000", afterPositions));

        assertThat(result.status()).isEqualTo("RECONCILED_INTERNAL_TRADE");
        assertThat(result.cashChange()).isEqualByComparingTo("-10000");
    }

    @Test
    void cashIncreaseMatchedByPositionSaleIsAnInternalTrade() {
        var instrument = UUID.randomUUID();
        var beforePositions = List.of(position(instrument, "20", "20000"));
        var afterPositions = List.of(position(instrument, "10", "10000"));

        var result = PortfolioCashflowReconciliationService.assess(
                snapshot("30000", "50000", beforePositions), snapshot("40000", "50000", afterPositions));

        assertThat(result.status()).isEqualTo("RECONCILED_INTERNAL_TRADE");
        assertThat(result.cashChange()).isEqualByComparingTo("10000");
    }

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

    private static PortfolioCashflowReconciliationService.Snapshot snapshot(
            String cash, String brokerValue, List<PortfolioCashflowReconciliationService.PositionBalance> positions) {
        return new PortfolioCashflowReconciliationService.Snapshot(
                new BigDecimal(cash), new BigDecimal(brokerValue), true, positions);
    }

    private static PortfolioCashflowReconciliationService.PositionBalance position(
            UUID instrument, String quantity, String marketValue) {
        return new PortfolioCashflowReconciliationService.PositionBalance(
                instrument, new BigDecimal(quantity), new BigDecimal(marketValue));
    }
}
