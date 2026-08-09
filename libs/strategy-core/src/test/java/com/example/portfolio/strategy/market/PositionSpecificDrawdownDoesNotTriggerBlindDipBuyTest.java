package com.example.portfolio.strategy.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PositionSpecificDrawdownDoesNotTriggerBlindDipBuyTest {
    @Test
    void concentratedPositionLossDoesNotEnableMarketDipMode() {
        var result =
                DrawdownEngine.classify(MarketDriven15PctAllowsEtfDipTest.input(-0.03, -0.04, 0.7, 0.2, 0.65, 0.3));

        assertThat(result.source()).isEqualTo(DrawdownEngine.Source.POSITION_SPECIFIC);
        assertThat(result.marketDriven()).isFalse();
    }
}
