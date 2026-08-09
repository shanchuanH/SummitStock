package com.example.portfolio.estimates;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NegativeRevisionBlocksQualityAddTest {
    @Test
    void strongNegativeBlocksNormalAndStarterQualityAdds() {
        var state = EstimateRevisionEngine.RevisionState.STRONGLY_NEGATIVE;

        assertThat(EstimateRevisionPolicy.blocksQualityAdd(state)).isTrue();
        assertThat(EstimateRevisionPolicy.normalAddAllowed(state)).isFalse();
        assertThat(EstimateRevisionPolicy.deepDiscountStarterAllowed(state)).isFalse();
        assertThat(EstimateRevisionPolicy.normalAddAllowed(EstimateRevisionEngine.RevisionState.FLAT))
                .isTrue();
    }
}
