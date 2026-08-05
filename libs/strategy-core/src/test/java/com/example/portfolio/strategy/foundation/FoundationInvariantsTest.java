package com.example.portfolio.strategy.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FoundationInvariantsTest {
    @Test
    void unvestedCompensationIsNeverLiquidOrTradable() {
        var eligibility = FoundationInvariants.unvestedCompensation();

        assertThat(eligibility.liquid()).isFalse();
        assertThat(eligibility.tradable()).isFalse();
        assertThat(eligibility.recommendationAllowed()).isFalse();
        assertThat(eligibility.liquidValue()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(eligibility.ruleIds()).containsExactly(RuleIds.UNVESTED_COMPENSATION);
    }

    @Test
    void emergencyFloorCannotBecomeDeployableCash() {
        assertThat(FoundationInvariants.deployableCash(new BigDecimal("25000"), new BigDecimal("20000")))
                .isEqualByComparingTo("5000");
        assertThat(FoundationInvariants.deployableCash(new BigDecimal("15000"), new BigDecimal("20000")))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
