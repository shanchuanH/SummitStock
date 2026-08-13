package com.example.portfolio.analysis.risk;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ImmutableRiskMultipleTest {
    @Test
    void trailingStopCannotRedefineOneR() {
        var frozenRisk = RiskMultiple.initialRiskPerShare(bd("100"), bd("90"));

        var beforeTrailingChange = RiskMultiple.currentR(bd("120"), bd("100"), frozenRisk);
        var afterTrailingChange = RiskMultiple.currentR(bd("120"), bd("100"), frozenRisk);

        assertThat(frozenRisk).isEqualByComparingTo("10");
        assertThat(beforeTrailingChange).isEqualByComparingTo("2");
        assertThat(afterTrailingChange).isEqualByComparingTo("2");
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
