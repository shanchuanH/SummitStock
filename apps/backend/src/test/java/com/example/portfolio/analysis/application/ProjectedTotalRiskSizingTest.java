package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ProjectedTotalRiskSizingTest {
    @Test
    void totalPortfolioRiskAndLiquidityBothCapTheFinalQuantity() {
        var base = PositionSizingV2Fixtures.valid(RecommendationAction.ADD);
        var input = withPortfolioLimits(base, "1500", "0.0200", "7");

        var result = PositionSizing.calculate(input);

        assertThat(result.quantityByRisk()).isEqualByComparingTo("40");
        assertThat(result.quantityByTotalRiskCap()).isEqualByComparingTo("10");
        assertThat(result.quantityByLiquidity()).isEqualByComparingTo("7");
        assertThat(result.quantityMax()).isEqualByComparingTo("7");
    }

    @Test
    void exhaustedTotalRiskBudgetPreventsAnAdd() {
        var base = PositionSizingV2Fixtures.valid(RecommendationAction.ADD);

        var result = PositionSizing.calculate(withPortfolioLimits(base, "1600", "0.0200", "100"));

        assertThat(result.quantityByTotalRiskCap()).isZero();
        assertThat(result.quantityMax()).isZero();
    }

    private static PositionSizing.Input withPortfolioLimits(
            PositionSizing.Input value, String currentRisk, String cap, String liquidity) {
        return new PositionSizing.Input(
                value.action(),
                value.investableAssets(),
                value.tradeRiskFraction(),
                value.entryPrice(),
                value.formalStop(),
                value.quotePrice(),
                value.currentQuantity(),
                value.currentMarketValue(),
                value.targetWeightMin(),
                value.weightCap(),
                value.trimTargetWeight(),
                value.deployableCash(),
                value.currentClusterOpenRiskAmount(),
                value.clusterRiskCapFraction(),
                value.starterFraction(),
                value.stopRequired(),
                value.priceQuality(),
                value.capitalQuality(),
                value.riskQuality(),
                value.priceFresh(),
                value.riskFresh(),
                value.classificationConfirmed(),
                value.providerHardError(),
                new BigDecimal(currentRisk),
                new BigDecimal(cap),
                new BigDecimal(liquidity),
                null);
    }
}
