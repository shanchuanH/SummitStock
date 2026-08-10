package com.example.portfolio.strategy.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.dip.EtfDipEngine;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarketDriven15PctAllowsEtfDipTest {
    @Test
    void healthyEmergencyAndQualifiedSetupCanDeployAtMarketDrivenFifteenPercent() {
        var drawdown = DrawdownEngine.classify(input(-0.13, -0.16, 0.25, 0.8, 0.2, 0.2));
        var dip = EtfDipEngine.evaluate(new EtfDipEngine.Input(
                new BigDecimal("0.15"),
                drawdown.marketDriven(),
                true,
                true,
                1,
                1,
                1,
                1,
                1,
                1,
                true,
                true,
                false,
                false,
                false,
                false,
                0,
                99));

        assertThat(drawdown.source()).isEqualTo(DrawdownEngine.Source.MARKET_DRIVEN);
        assertThat(dip.action()).isEqualTo("DEPLOY_TRANCHE");
    }

    static DrawdownEngine.Input input(
            double spy, double qqq, double breadth, double stress, double position, double cluster) {
        return new DrawdownEngine.Input(
                new BigDecimal("85000"),
                new BigDecimal("100000"),
                spy,
                qqq,
                breadth,
                stress,
                position,
                cluster,
                EvidenceQuality.HEALTHY);
    }
}
