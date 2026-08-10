package com.example.portfolio.valuation;

import com.example.portfolio.estimates.EstimateRevisionEngine;
import java.math.BigDecimal;

public final class QualityValuationDecisionRule {
    public Decision evaluate(Input input) {
        if (!input.qualityClassification()) return Decision.HOLD;
        if (input.portfolioBlocked() || input.thesisInvalidated() || input.catastrophicStop()) {
            return Decision.DO_NOT_ADD;
        }
        if (!healthy(input.health())) return Decision.DO_NOT_ADD;
        if (input.revision() == EstimateRevisionEngine.RevisionState.STRONGLY_NEGATIVE) {
            return Decision.DO_NOT_ADD;
        }
        var belowNormalMax = input.currentWeight().compareTo(input.normalMax()) < 0;
        if (belowNormalMax
                && input.valuation() == ValuationEngineV2.ValuationState.DEEP_DISCOUNT
                && (input.priorStarterCount() == 0 || input.independentConfirmation())) {
            return Decision.STARTER_BUY;
        }
        if (belowNormalMax
                && acceptableNormalValuation(input.valuation())
                && atLeastFlat(input.revision())
                && (input.priceState() == PriceState.UPTREND || input.priceState() == PriceState.REVERSAL_CONFIRMED)) {
            return Decision.ADD;
        }
        return Decision.HOLD;
    }

    private static boolean healthy(ValuationEngineV2.CompanyHealth value) {
        return value == ValuationEngineV2.CompanyHealth.STRONG || value == ValuationEngineV2.CompanyHealth.HEALTHY;
    }

    private static boolean acceptableNormalValuation(ValuationEngineV2.ValuationState value) {
        return value == ValuationEngineV2.ValuationState.DEEP_DISCOUNT
                || value == ValuationEngineV2.ValuationState.ATTRACTIVE
                || value == ValuationEngineV2.ValuationState.FAIR;
    }

    private static boolean atLeastFlat(EstimateRevisionEngine.RevisionState value) {
        return value == EstimateRevisionEngine.RevisionState.STRONGLY_POSITIVE
                || value == EstimateRevisionEngine.RevisionState.POSITIVE
                || value == EstimateRevisionEngine.RevisionState.FLAT;
    }

    public enum Decision {
        STARTER_BUY,
        ADD,
        HOLD,
        DO_NOT_ADD
    }

    public enum PriceState {
        UPTREND,
        REVERSAL_CONFIRMED,
        DOWNTREND,
        MISSING
    }

    public record Input(
            boolean qualityClassification,
            ValuationEngineV2.CompanyHealth health,
            ValuationEngineV2.ValuationState valuation,
            EstimateRevisionEngine.RevisionState revision,
            PriceState priceState,
            boolean portfolioBlocked,
            boolean thesisInvalidated,
            boolean catastrophicStop,
            BigDecimal currentWeight,
            BigDecimal normalMax,
            int priorStarterCount,
            boolean independentConfirmation) {}
}
