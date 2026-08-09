package com.example.portfolio.strategy.market;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ClusterSpecificDrawdownTest {
    @Test
    void clusterContributionIsDistinguishedFromOnePositionAndTheMarket() {
        var result =
                DrawdownEngine.classify(MarketDriven15PctAllowsEtfDipTest.input(-0.02, -0.04, 0.7, 0.2, 0.30, 0.60));

        assertThat(result.source()).isEqualTo(DrawdownEngine.Source.CLUSTER_SPECIFIC);
        assertThat(result.marketDriven()).isFalse();
    }
}
