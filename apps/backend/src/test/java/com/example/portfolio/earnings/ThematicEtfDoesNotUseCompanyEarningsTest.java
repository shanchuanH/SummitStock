package com.example.portfolio.earnings;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.EarningsPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ThematicEtfDoesNotUseCompanyEarningsTest {
    @Test
    void companyEarningsPolicyIsNotApplicableToThematicEtfs() {
        var result = EarningsPolicy.review(new EarningsPolicy.Input(
                HoldingClassification.THEMATIC_ETF, 12, BigDecimal.ZERO, true, EarningsPolicy.EventRisk.EXTREME, true));

        assertThat(result.action()).isEqualTo("NOT_APPLICABLE");
        assertThat(result.ruleIds()).isEmpty();
    }
}
