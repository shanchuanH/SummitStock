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
                && stabilized(input.priceState())
                && (input.priorStarterCount() == 0 || input.independentConfirmation())) {
            return Decision.STARTER_BUY;
        }
        if (belowNormalMax && normalAddValuationGate(input.valuation(), input.revision(), input.priceState())) {
            return Decision.ADD;
        }
        return Decision.HOLD;
    }

    private static boolean healthy(ValuationEngineV2.CompanyHealth value) {
        return value == ValuationEngineV2.CompanyHealth.STRONG || value == ValuationEngineV2.CompanyHealth.HEALTHY;
    }

    private static boolean normalAddValuationGate(
            ValuationEngineV2.ValuationState valuation,
            EstimateRevisionEngine.RevisionState revision,
            PriceState priceState) {
        if (valuation == ValuationEngineV2.ValuationState.ATTRACTIVE) {
            return atLeastFlat(revision) && confirmed(priceState);
        }
        if (valuation == ValuationEngineV2.ValuationState.FAIR) {
            return improving(revision) && strongConfirmation(priceState);
        }
        return false;
    }

    private static boolean atLeastFlat(EstimateRevisionEngine.RevisionState value) {
        return value == EstimateRevisionEngine.RevisionState.STRONGLY_POSITIVE
                || value == EstimateRevisionEngine.RevisionState.POSITIVE
                || value == EstimateRevisionEngine.RevisionState.FLAT;
    }

    private static boolean stabilized(PriceState value) {
        return value == PriceState.NEUTRAL || value == PriceState.REVERSAL_SETUP || confirmed(value);
    }

    private static boolean confirmed(PriceState value) {
        return value == PriceState.UPTREND
                || value == PriceState.STRONG_UPTREND
                || value == PriceState.REVERSAL_CONFIRMED;
    }

    private static boolean improving(EstimateRevisionEngine.RevisionState value) {
        return value == EstimateRevisionEngine.RevisionState.STRONGLY_POSITIVE
                || value == EstimateRevisionEngine.RevisionState.POSITIVE;
    }

    private static boolean strongConfirmation(PriceState value) {
        return value == PriceState.STRONG_UPTREND || value == PriceState.REVERSAL_CONFIRMED;
    }

    public enum Decision {
        STARTER_BUY,
        ADD,
        HOLD,
        DO_NOT_ADD
    }

    public enum PriceState {
        STRONG_UPTREND,
        UPTREND,
        NEUTRAL,
        WEAK,
        REVERSAL_SETUP,
        REVERSAL_CONFIRMED,
        DOWNTREND,
        BREAKDOWN,
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
