package com.example.portfolio.earnings;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.EarningsPolicy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QualityEarningsPolicyTest {
    @Test
    void holdsCoreQualityAndOnlyTrimsWhenOverRiskLimit() {
        var hold = EarningsPolicy.review(new EarningsPolicy.Input(
                HoldingClassification.QUALITY_STOCK, 10, BigDecimal.TWO, false, EarningsPolicy.EventRisk.HIGH, false));
        var trim = EarningsPolicy.review(new EarningsPolicy.Input(
                HoldingClassification.QUALITY_STOCK, 10, BigDecimal.TWO, true, EarningsPolicy.EventRisk.HIGH, false));

        assertThat(hold.action()).isEqualTo("HOLD_THROUGH_EVENT");
        assertThat(trim.action()).isEqualTo("TRIM_TO_RISK_LIMIT");
    }
}
