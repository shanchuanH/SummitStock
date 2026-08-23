package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.policy.DecisionReasonTag;
import org.junit.jupiter.api.Test;

class DecisionReasonTagsTest {
    @Test
    void parsesOnlyStructuredReasonTagsAndFailsClosedForNarrativeText() {
        assertThat(DecisionReasonTags.parse("[\"COST_BASIS_ANCHOR\",\"VALUATION\"]"))
                .containsExactlyInAnyOrder(DecisionReasonTag.COST_BASIS_ANCHOR, DecisionReasonTag.VALUATION);
        assertThat(DecisionReasonTags.parse("I want to wait for break even at my cost basis"))
                .isEmpty();
        assertThat(DecisionReasonTags.parse("[\"UNKNOWN_TAG\"]")).isEmpty();
    }
}
