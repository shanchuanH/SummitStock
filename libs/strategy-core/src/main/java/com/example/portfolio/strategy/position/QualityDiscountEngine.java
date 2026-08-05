package com.example.portfolio.strategy.position;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import java.util.List;

public final class QualityDiscountEngine {
    private QualityDiscountEngine() {}

    public static Result evaluate(Input input) {
        if (!input.fundamentalsHealthy() || !input.revisionsImproving()) {
            return new Result("DO_NOT_ADD", BigDecimal.ZERO, List.of(RuleIds.VALUATION_REVISIONS_FALLING));
        }
        if (!input.valuationDiscount()) return new Result("HOLD_QUALITY_CORE", BigDecimal.ZERO, List.of());
        if (!input.priceStabilized()) {
            return new Result(
                    "WAIT_FOR_STABILIZATION", BigDecimal.ZERO, List.of(RuleIds.VALUATION_STABILIZATION_REQUIRED));
        }
        if (!input.portfolioCapacity() || input.discountTacticalWeight().compareTo(new BigDecimal("0.03")) >= 0) {
            return new Result("DO_NOT_ADD", BigDecimal.ZERO, List.of(RuleIds.VALUATION_TACTICAL_CAP));
        }
        var secondTranche = input.discountTacticalWeight().compareTo(new BigDecimal("0.01")) >= 0;
        return new Result(
                secondTranche ? "ADD_SECOND_TRANCHE" : "ADD_1_PERCENT_STARTER", new BigDecimal("0.01"), List.of());
    }

    public record Input(
            boolean fundamentalsHealthy,
            boolean valuationDiscount,
            boolean revisionsImproving,
            boolean priceStabilized,
            boolean portfolioCapacity,
            BigDecimal discountTacticalWeight) {}

    public record Result(String action, BigDecimal addWeight, List<String> ruleIds) {}
}
