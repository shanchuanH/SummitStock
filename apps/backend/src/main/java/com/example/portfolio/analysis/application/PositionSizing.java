package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.decision.RecommendationSizingService;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PositionSizing {
    private PositionSizing() {}

    public static Result calculate(Input input) {
        return calculate(input, true, true);
    }

    public static Result calculate(Input input, boolean requireHealthyPrice, boolean requireReadyRisk) {
        if (!eligible(input, requireHealthyPrice, requireReadyRisk)) return unavailable();
        if (RecommendationSizingService.requiresSellSizing(input.action())) return sell(input);
        if (!RecommendationSizingService.requiresBuySizing(input.action())) return unavailable();
        return buy(input);
    }

    private static Result buy(Input input) {
        var byWeight = floor(input.investableAssets()
                .multiply(input.weightCap())
                .subtract(input.currentMarketValue())
                .max(BigDecimal.ZERO)
                .divide(input.quotePrice(), 12, RoundingMode.DOWN));
        var byCash =
                floor(input.deployableCash().max(BigDecimal.ZERO).divide(input.quotePrice(), 12, RoundingMode.DOWN));

        BigDecimal byRisk;
        BigDecimal byCluster;
        if (input.stopRequired()) {
            var riskPerShare = input.entryPrice().subtract(input.formalStop()).abs();
            if (riskPerShare.signum() == 0) return unavailable();
            var riskAmount = input.investableAssets().multiply(input.tradeRiskFraction());
            byRisk = floor(riskAmount.divide(riskPerShare, 12, RoundingMode.DOWN));
            var remainingClusterRiskAmount = input.investableAssets()
                    .multiply(input.clusterRiskCapFraction())
                    .subtract(input.currentClusterOpenRiskAmount())
                    .max(BigDecimal.ZERO);
            byCluster = floor(remainingClusterRiskAmount.divide(riskPerShare, 12, RoundingMode.DOWN));
        } else {
            byRisk = byWeight;
            byCluster = byWeight;
        }

        var maximum = min(byRisk, byWeight, byCash, byCluster);
        var targetMinQuantity = input.targetWeightMin() == null
                ? BigDecimal.ZERO
                : floor(input.investableAssets()
                        .multiply(input.targetWeightMin())
                        .subtract(input.currentMarketValue())
                        .max(BigDecimal.ZERO)
                        .divide(input.quotePrice(), 12, RoundingMode.DOWN));
        var minimum = targetMinQuantity.min(maximum);
        if (input.action() == RecommendationAction.STARTER_BUY) {
            return new Result(
                    true,
                    scale(minimum, input.starterFraction()),
                    scale(maximum, input.starterFraction()),
                    scale(byRisk, input.starterFraction()),
                    scale(byWeight, input.starterFraction()),
                    scale(byCash, input.starterFraction()),
                    scale(byCluster, input.starterFraction()));
        }
        return new Result(true, minimum, maximum, byRisk, byWeight, byCash, byCluster);
    }

    private static Result sell(Input input) {
        var quantity =
                switch (input.action()) {
                    case EXIT -> floor(input.currentQuantity());
                    case REDUCE_HALF -> floor(input.currentQuantity().multiply(new BigDecimal("0.50")));
                    case TRIM -> trimQuantity(input);
                    default -> BigDecimal.ZERO;
                };
        return new Result(true, quantity, quantity, null, null, null, null);
    }

    private static BigDecimal trimQuantity(Input input) {
        var target = input.trimTargetWeight() != null ? input.trimTargetWeight() : input.weightCap();
        var excess = input.currentMarketValue()
                .subtract(input.investableAssets().multiply(target))
                .max(BigDecimal.ZERO);
        return ceil(excess.divide(input.quotePrice(), 12, RoundingMode.UP)).min(floor(input.currentQuantity()));
    }

    private static boolean eligible(Input input, boolean requireHealthyPrice, boolean requireReadyRisk) {
        if (input.investableAssets() == null
                || input.investableAssets().signum() <= 0
                || input.quotePrice() == null
                || input.quotePrice().signum() <= 0
                || input.capitalQuality() != EvidenceQuality.HEALTHY
                || !input.classificationConfirmed()
                || input.providerHardError()) {
            return false;
        }
        if (requireHealthyPrice && (input.priceQuality() != EvidenceQuality.HEALTHY || !input.priceFresh()))
            return false;
        if (requireReadyRisk && (input.riskQuality() != EvidenceQuality.HEALTHY || !input.riskFresh())) return false;
        return !input.stopRequired()
                || (input.formalStop() != null
                        && input.entryPrice() != null
                        && input.entryPrice().signum() > 0);
    }

    private static Result unavailable() {
        return new Result(false, null, null, null, null, null, null);
    }

    private static BigDecimal floor(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR);
    }

    private static BigDecimal ceil(BigDecimal value) {
        return value.setScale(0, RoundingMode.CEILING);
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal fraction) {
        return floor(value.multiply(fraction));
    }

    private static BigDecimal min(BigDecimal first, BigDecimal... rest) {
        var value = first;
        for (var candidate : rest) value = value.min(candidate);
        return value;
    }

    public record Input(
            RecommendationAction action,
            BigDecimal investableAssets,
            BigDecimal tradeRiskFraction,
            BigDecimal entryPrice,
            BigDecimal formalStop,
            BigDecimal quotePrice,
            BigDecimal currentQuantity,
            BigDecimal currentMarketValue,
            BigDecimal targetWeightMin,
            BigDecimal weightCap,
            BigDecimal trimTargetWeight,
            BigDecimal deployableCash,
            BigDecimal currentClusterOpenRiskAmount,
            BigDecimal clusterRiskCapFraction,
            BigDecimal starterFraction,
            boolean stopRequired,
            EvidenceQuality priceQuality,
            EvidenceQuality capitalQuality,
            EvidenceQuality riskQuality,
            boolean priceFresh,
            boolean riskFresh,
            boolean classificationConfirmed,
            boolean providerHardError) {}

    public record Result(
            boolean exactQuantityAllowed,
            BigDecimal quantityMin,
            BigDecimal quantityMax,
            BigDecimal quantityByRisk,
            BigDecimal quantityByWeightCap,
            BigDecimal quantityByAvailableCash,
            BigDecimal quantityByClusterCap) {}
}
