package com.example.portfolio.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.estimates.EstimateRevisionEngine;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DeepDiscountStarterRuleTest {
    @Test
    void permitsStarterOnlyAfterPriceStabilizesAndRequiresIndependentSecondConfirmation() {
        var rule = new QualityValuationDecisionRule();

        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        ValuationEngineV2.ValuationState.DEEP_DISCOUNT,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        QualityValuationDecisionRule.PriceState.DOWNTREND,
                        0,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        ValuationEngineV2.ValuationState.DEEP_DISCOUNT,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        QualityValuationDecisionRule.PriceState.REVERSAL_SETUP,
                        0,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.STARTER_BUY);
        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        ValuationEngineV2.ValuationState.DEEP_DISCOUNT,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        QualityValuationDecisionRule.PriceState.REVERSAL_SETUP,
                        1,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        ValuationEngineV2.ValuationState.DEEP_DISCOUNT,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        QualityValuationDecisionRule.PriceState.REVERSAL_CONFIRMED,
                        1,
                        true)))
                .isEqualTo(QualityValuationDecisionRule.Decision.STARTER_BUY);
    }

    @Test
    void fairValuationNeedsImprovingRevisionsAndStrongPriceConfirmation() {
        var rule = new QualityValuationDecisionRule();

        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.STRONG,
                        ValuationEngineV2.ValuationState.FAIR,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        QualityValuationDecisionRule.PriceState.UPTREND,
                        0,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.STRONG,
                        ValuationEngineV2.ValuationState.FAIR,
                        EstimateRevisionEngine.RevisionState.POSITIVE,
                        QualityValuationDecisionRule.PriceState.UPTREND,
                        0,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
        assertThat(rule.evaluate(input(
                        ValuationEngineV2.CompanyHealth.STRONG,
                        ValuationEngineV2.ValuationState.FAIR,
                        EstimateRevisionEngine.RevisionState.POSITIVE,
                        QualityValuationDecisionRule.PriceState.STRONG_UPTREND,
                        0,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.ADD);
    }

    @Test
    void fairValuationAlsoNeedsStrongHealthMeaningfulGapAndRiskCapacity() {
        var rule = new QualityValuationDecisionRule();
        var base = input(
                ValuationEngineV2.CompanyHealth.HEALTHY,
                ValuationEngineV2.ValuationState.FAIR,
                EstimateRevisionEngine.RevisionState.POSITIVE,
                QualityValuationDecisionRule.PriceState.STRONG_UPTREND,
                0,
                false);

        assertThat(rule.evaluate(base)).isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
        assertThat(rule.evaluate(new QualityValuationDecisionRule.Input(
                        true,
                        ValuationEngineV2.CompanyHealth.STRONG,
                        ValuationEngineV2.ValuationState.FAIR,
                        EstimateRevisionEngine.RevisionState.POSITIVE,
                        QualityValuationDecisionRule.PriceState.STRONG_UPTREND,
                        false,
                        false,
                        false,
                        new BigDecimal("0.05"),
                        new BigDecimal("0.04"),
                        new BigDecimal("0.12"),
                        true,
                        0,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
        assertThat(rule.evaluate(new QualityValuationDecisionRule.Input(
                        true,
                        ValuationEngineV2.CompanyHealth.STRONG,
                        ValuationEngineV2.ValuationState.FAIR,
                        EstimateRevisionEngine.RevisionState.POSITIVE,
                        QualityValuationDecisionRule.PriceState.STRONG_UPTREND,
                        false,
                        false,
                        false,
                        new BigDecimal("0.01"),
                        new BigDecimal("0.04"),
                        new BigDecimal("0.12"),
                        false,
                        0,
                        false)))
                .isEqualTo(QualityValuationDecisionRule.Decision.HOLD);
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
                new BigDecimal("0.04"),
                new BigDecimal("0.12"),
                true,
                starters,
                confirmation);
    }
}
