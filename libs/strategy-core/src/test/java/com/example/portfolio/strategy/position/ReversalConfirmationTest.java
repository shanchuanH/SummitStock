package com.example.portfolio.strategy.position;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ReversalConfirmationTest {
    @Test
    void requiresAtLeastTwoIndependentConfirmations() {
        var engine = new PriceStateEngine();
        var one = engine.evaluate(input(true, false));
        var two = engine.evaluate(input(true, true));

        assertThat(one.state()).isEqualTo(PriceStateEngine.State.REVERSAL_SETUP);
        assertThat(two.state()).isEqualTo(PriceStateEngine.State.REVERSAL_CONFIRMED);
    }

    private static PriceStateEngine.Input input(boolean reclaim, boolean higherLow) {
        return new PriceStateEngine.Input(
                90, 90, 100, 110, 45, -1, -0.18, 0.95, reclaim, higherLow, false, false, false, false, true);
    }
}
