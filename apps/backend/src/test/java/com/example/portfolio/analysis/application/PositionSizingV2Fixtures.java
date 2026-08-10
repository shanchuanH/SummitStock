package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;

final class PositionSizingV2Fixtures {
    private PositionSizingV2Fixtures() {}

    static PositionSizing.Input valid(RecommendationAction action) {
        return input(
                action,
                "80000",
                "3000",
                "800",
                EvidenceQuality.HEALTHY,
                true,
                "90",
                "60",
                "6000",
                "0.15",
                "0.15",
                "0.25");
    }

    static PositionSizing.Input input(
            RecommendationAction action,
            String investable,
            String deployable,
            String clusterRiskAmount,
            EvidenceQuality priceQuality,
            boolean priceFresh,
            String stop,
            String quantity,
            String marketValue,
            String weightCap,
            String trimTarget,
            String starterFraction) {
        return new PositionSizing.Input(
                action,
                decimal(investable),
                new BigDecimal("0.005"),
                new BigDecimal("100"),
                decimal(stop),
                new BigDecimal("100"),
                decimal(quantity),
                decimal(marketValue),
                new BigDecimal("0.04"),
                decimal(weightCap),
                decimal(trimTarget),
                decimal(deployable),
                decimal(clusterRiskAmount),
                new BigDecimal("0.02"),
                decimal(starterFraction),
                true,
                priceQuality,
                EvidenceQuality.HEALTHY,
                EvidenceQuality.HEALTHY,
                priceFresh,
                true,
                true,
                false);
    }

    static PositionSizing.Input eligibility(
            PositionSizing.Input value,
            EvidenceQuality capitalQuality,
            EvidenceQuality riskQuality,
            boolean riskFresh,
            boolean classificationConfirmed,
            boolean providerHardError) {
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
                capitalQuality,
                riskQuality,
                value.priceFresh(),
                riskFresh,
                classificationConfirmed,
                providerHardError);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
