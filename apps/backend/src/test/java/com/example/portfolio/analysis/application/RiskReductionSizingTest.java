package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class RiskReductionSizingTest {
    @Test
    void exactExitPreservesFractionalSharesWithoutNewRiskEvidence() {
        var base = PositionSizingV2Fixtures.valid(RecommendationAction.EXIT);
        var input = new PositionSizing.Input(
                base.action(),
                null,
                base.tradeRiskFraction(),
                null,
                null,
                null,
                new BigDecimal("0.75"),
                null,
                base.targetWeightMin(),
                base.weightCap(),
                base.trimTargetWeight(),
                BigDecimal.ZERO,
                null,
                base.clusterRiskCapFraction(),
                base.starterFraction(),
                true,
                EvidenceQuality.MISSING,
                EvidenceQuality.MISSING,
                EvidenceQuality.MISSING,
                false,
                false,
                true,
                true,
                null,
                base.totalRiskCapFraction(),
                null,
                null);

        var result = PositionSizing.calculate(input);

        assertThat(result.exactQuantityAllowed()).isTrue();
        assertThat(result.quantityMin()).isEqualByComparingTo("0.75");
        assertThat(result.quantityMax()).isEqualByComparingTo("0.75");
    }
}
