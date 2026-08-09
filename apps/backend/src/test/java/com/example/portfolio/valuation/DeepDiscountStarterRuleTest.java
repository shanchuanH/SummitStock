package com.example.portfolio.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.estimates.EstimateRevisionEngine;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeepDiscountStarterRuleTest {
    @Test
    void permitsSmallStarterDespiteWeakTrendButRequiresIndependentSecondConfirmation() {
        var rule = new QualityValuationDecisionRule();

        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        ValuationEngineV2.ValuationState.DEEP_DISCOUNT,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        QualityValuationDecisionRule.PriceState.DOWNTREND,
                        0,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.STARTER_BUY);
        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        ValuationEngineV2.ValuationState.DEEP_DISCOUNT,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        QualityValuationDecisionRule.PriceState.DOWNTREND,
                        1,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        ValuationEngineV2.ValuationState.DEEP_DISCOUNT,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        QualityValuationDecisionRule.PriceState.DOWNTREND,
                        1,
                        true)))
                .isEqualTo(QualityValuationDecisionRule.Decision.STARTER_BUY);
    }

    static QualityValuationDecisionRule.Input input(
            ValuationEngineV2.CompanyHealth health,
            ValuationEngineV2.ValuationState valuation,
            EstimateRevisionEngine.RevisionState revision,
            QualityValuationDecisionRule.PriceState price,
            int starters,
            boolean confirmation) {
        return new QualityValuationDecisionRule.Input(
                true,
                health,
                valuation,
                revision,
                price,
                false,
                false,
                false,
                new BigDecimal("0.01"),
                new BigDecimal("0.12"),
                starters,
                confirmation);
    }
}
