package com.example.portfolio.analysis.application;

import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PositionSizing {
    private PositionSizing() {}

    public static Result calculate(Input input) {
        if (input.quality() != EvidenceQuality.HEALTHY
                || !input.quoteFresh()
                || input.formalStop() == null
                || input.entryPrice() == null
                || input.quotePrice() == null
                || input.quotePrice().signum() <= 0) {
            return new Result(false, null, null, null, null, null, null);
        }
        var riskPerShare = input.entryPrice().subtract(input.formalStop()).abs();
        if (riskPerShare.signum() == 0) return new Result(false, null, null, null, null, null, null);
        var riskAmount = input.portfolioEquity().multiply(input.tradeRiskFraction());
        var byRisk = floor(riskAmount.divide(riskPerShare, 12, RoundingMode.DOWN));
        var targetMinDollar = input.liquidPortfolioValue().multiply(input.targetWeightMin());
        var targetMaxDollar = input.liquidPortfolioValue().multiply(input.targetWeightMax());
        var differenceMin = targetMinDollar.subtract(input.currentMarketValue()).max(BigDecimal.ZERO);
        var differenceMax = targetMaxDollar.subtract(input.currentMarketValue()).max(BigDecimal.ZERO);
        var quantityMin = floor(differenceMin.divide(input.quotePrice(), 12, RoundingMode.DOWN));
        var byTarget = floor(differenceMax.divide(input.quotePrice(), 12, RoundingMode.DOWN));
        var byWeight = floor(input.liquidPortfolioValue()
                .multiply(input.weightCap())
                .subtract(input.currentMarketValue())
                .max(BigDecimal.ZERO)
                .divide(input.quotePrice(), 12, RoundingMode.DOWN));
        var byCash =
                floor(input.availableCash().max(BigDecimal.ZERO).divide(input.quotePrice(), 12, RoundingMode.DOWN));
        var byCluster =
                floor(input.clusterCapacity().max(BigDecimal.ZERO).divide(input.quotePrice(), 12, RoundingMode.DOWN));
        var maximum = min(byRisk, byTarget, byWeight, byCash, byCluster);
        return new Result(true, quantityMin.min(maximum), maximum, byRisk, byWeight, byCash, byCluster);
    }

    private static BigDecimal floor(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR);
    }

    private static BigDecimal min(BigDecimal first, BigDecimal... rest) {
        var value = first;
        for (var candidate : rest) value = value.min(candidate);
        return value;
    }

    public record Input(
            BigDecimal portfolioEquity,
            BigDecimal liquidPortfolioValue,
            BigDecimal tradeRiskFraction,
            BigDecimal entryPrice,
            BigDecimal formalStop,
            BigDecimal quotePrice,
            BigDecimal currentMarketValue,
            BigDecimal targetWeightMin,
            BigDecimal targetWeightMax,
            BigDecimal weightCap,
            BigDecimal availableCash,
            BigDecimal clusterCapacity,
            EvidenceQuality quality,
            boolean quoteFresh) {}

    public record Result(
            boolean exactQuantityAllowed,
            BigDecimal quantityMin,
            BigDecimal quantityMax,
            BigDecimal quantityByRisk,
            BigDecimal quantityByWeightCap,
            BigDecimal quantityByAvailableCash,
            BigDecimal quantityByClusterCap) {}
}
