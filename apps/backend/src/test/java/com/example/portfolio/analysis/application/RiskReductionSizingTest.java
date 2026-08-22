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

    @Test
    void exitStillReturnsExactFractionalQuantityWhenRiskProjectionInputsAreMissing() {
        var input = reduction(RecommendationAction.EXIT, "0.75", "75", "0.50");

        var result = PositionSizing.calculate(input);

        assertThat(result.exactQuantityAllowed()).isTrue();
        assertThat(result.quantityMax()).isEqualByComparingTo("0.75");
        assertThat(result.projectedPositionWeight()).isZero();
        assertThat(result.projectedTotalRisk()).isNull();
        assertThat(result.projectedClusterRisk()).isNull();
        assertThat(result.limitingConstraint()).isEqualTo("RISK_PROJECTION_UNAVAILABLE");
    }

    @Test
    void reduceHalfPreservesFractionalQuantityWhenRiskProjectionInputsAreMissing() {
        var result = PositionSizing.calculate(reduction(RecommendationAction.REDUCE_HALF, "0.75", "75", "0.50"));

        assertThat(result.exactQuantityAllowed()).isTrue();
        assertThat(result.quantityMin()).isEqualByComparingTo("0.375");
        assertThat(result.quantityMax()).isEqualByComparingTo("0.375");
        assertThat(result.projectedTotalRisk()).isNull();
        assertThat(result.projectedClusterRisk()).isNull();
        assertThat(result.limitingConstraint()).isEqualTo("RISK_PROJECTION_UNAVAILABLE");
    }

    @Test
    void trimCanReachAFractionalTargetBoundaryWithoutRoundingToZeroOrOneShare() {
        var result = PositionSizing.calculate(reduction(RecommendationAction.TRIM, "0.75", "75", "0.50"));

        assertThat(result.exactQuantityAllowed()).isTrue();
        assertThat(result.quantityMin()).isEqualByComparingTo("0.25");
        assertThat(result.quantityMax()).isEqualByComparingTo("0.25");
        assertThat(result.limitingConstraint()).isEqualTo("RISK_PROJECTION_UNAVAILABLE");
    }

    private static PositionSizing.Input reduction(
            RecommendationAction action, String quantity, String marketValue, String trimTargetWeight) {
        var base = PositionSizingV2Fixtures.valid(action);
        return new PositionSizing.Input(
                action,
                new BigDecimal("100"),
                base.tradeRiskFraction(),
                new BigDecimal("100"),
                new BigDecimal("90"),
                new BigDecimal("100"),
                new BigDecimal(quantity),
                new BigDecimal(marketValue),
                base.targetWeightMin(),
                base.weightCap(),
                new BigDecimal(trimTargetWeight),
                BigDecimal.ZERO,
                null,
                base.clusterRiskCapFraction(),
                base.starterFraction(),
                true,
                EvidenceQuality.HEALTHY,
                EvidenceQuality.MISSING,
                EvidenceQuality.MISSING,
                true,
                false,
                true,
                true,
                null,
                base.totalRiskCapFraction(),
                null,
                null);
    }
}
