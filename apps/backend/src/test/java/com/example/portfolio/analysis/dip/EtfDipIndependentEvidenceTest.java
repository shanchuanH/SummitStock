package com.example.portfolio.analysis.dip;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class EtfDipIndependentEvidenceTest {
    @Test
    void volatilityTermStructureIsDerivedFromSpotAndThreeMonthVix() {
        assertThat(EtfDipEventService.volatilityTermScore(bd("30"), bd("25"))).isEqualTo(1);
        assertThat(EtfDipEventService.volatilityTermScore(bd("18"), bd("20"))).isZero();
        assertThat(EtfDipEventService.volatilityTermScore(null, bd("20"))).isZero();
    }

    private static BigDecimal bd(String value) {
        return new BigDecimal(value);
    }
}
