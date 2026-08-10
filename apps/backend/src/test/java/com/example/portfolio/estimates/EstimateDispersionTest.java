package com.example.portfolio.estimates;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EstimateDispersionTest {
    @Test
    void normalizesEstimateRangeByAbsoluteMeanAndRejectsInvalidInputs() {
        assertThat(EstimateDispersion.calculate(bd("10"), bd("12"), bd("8"))).isEqualByComparingTo("0.4");
        assertThat(EstimateDispersion.calculate(BigDecimal.ZERO, bd("1"), bd("-1")))
                .isNull();
        assertThat(EstimateDispersion.calculate(bd("10"), bd("8"), bd("12"))).isNull();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
