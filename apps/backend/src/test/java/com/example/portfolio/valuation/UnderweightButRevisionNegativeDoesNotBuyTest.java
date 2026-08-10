package com.example.portfolio.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.estimates.EstimateRevisionEngine;
import org.junit.jupiter.api.Test;

class UnderweightButRevisionNegativeDoesNotBuyTest {
    @Test
    void strongNegativeRevisionBlocksEvenDeepDiscount() {
        var decision = new QualityValuationDecisionRule()
                .evaluate(DeepDiscountStarterRuleTest.input(
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        ValuationEngineV2.ValuationState.DEEP_DISCOUNT,
                        EstimateRevisionEngine.RevisionState.STRONGLY_NEGATIVE,
                        QualityValuationDecisionRule.PriceState.UPTREND,
                        0,
                        false));

        assertThat(decision).isEqualTo(QualityValuationDecisionRule.Decision.DO_NOT_ADD);
    }
}
