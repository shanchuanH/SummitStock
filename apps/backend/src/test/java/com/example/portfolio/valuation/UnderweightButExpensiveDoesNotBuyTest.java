package com.example.portfolio.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.estimates.EstimateRevisionEngine;
import org.junit.jupiter.api.Test;

class UnderweightButExpensiveDoesNotBuyTest {
    @Test
    void lowWeightIsNotAnInputThatCanOverrideExtremeValuation() {
        var decision = new QualityValuationDecisionRule()
                .evaluate(DeepDiscountStarterRuleTest.input(
                        ValuationEngineV2.CompanyHealth.STRONG,
                        ValuationEngineV2.ValuationState.EXTREME,
                        EstimateRevisionEngine.RevisionState.POSITIVE,
                        QualityValuationDecisionRule.PriceState.UPTREND,
                        0,
                        false));

        assertThat(decision).isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
    }
}
