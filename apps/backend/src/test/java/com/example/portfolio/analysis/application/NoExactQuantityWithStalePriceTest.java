package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.market.EvidenceQuality;
import org.junit.jupiter.api.Test;

class NoExactQuantityWithStalePriceTest {
    @Test
    void stalePriceAndMissingRequiredStopBothBlockExactQuantity() {
        var stale = PositionSizingV2Fixtures.input(
                RecommendationAction.ADD,
                "80000",
                "3000",
                "800",
                EvidenceQuality.STALE,
                false,
                "90",
                "60",
                "6000",
                "0.15",
                "0.15",
                "0.25");
        var missingStop = PositionSizingV2Fixtures.input(
                RecommendationAction.ADD,
                "80000",
                "3000",
                "800",
                EvidenceQuality.HEALTHY,
                true,
                null,
                "60",
                "6000",
                "0.15",
                "0.15",
                "0.25");

        assertThat(PositionSizing.calculate(stale).exactQuantityAllowed()).isFalse();
        assertThat(PositionSizing.calculate(missingStop).exactQuantityAllowed()).isFalse();
    }

    @Test
    void partialCapitalStaleRiskUnconfirmedClassificationAndProviderErrorsBlockPrecision() {
        var valid = PositionSizingV2Fixtures.valid(RecommendationAction.ADD);

        assertThat(PositionSizing.calculate(PositionSizingV2Fixtures.eligibility(
                                valid, EvidenceQuality.PARTIAL, EvidenceQuality.HEALTHY, true, true, false))
                        .exactQuantityAllowed())
                .isFalse();
        assertThat(PositionSizing.calculate(PositionSizingV2Fixtures.eligibility(
                                valid, EvidenceQuality.HEALTHY, EvidenceQuality.STALE, false, true, false))
                        .exactQuantityAllowed())
                .isFalse();
        assertThat(PositionSizing.calculate(PositionSizingV2Fixtures.eligibility(
                                valid, EvidenceQuality.HEALTHY, EvidenceQuality.HEALTHY, true, false, false))
                        .exactQuantityAllowed())
                .isFalse();
        assertThat(PositionSizing.calculate(PositionSizingV2Fixtures.eligibility(
                                valid, EvidenceQuality.HEALTHY, EvidenceQuality.HEALTHY, true, true, true))
                        .exactQuantityAllowed())
                .isFalse();
    }
}
