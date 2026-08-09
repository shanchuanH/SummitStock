package com.example.portfolio.estimates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class EstimateRevisionEngineTest {
    @Test
    void computesSevenThirtyAndNinetyDayEpsAndRevenueRevisions() {
        var now = Instant.parse("2026-08-01T00:00:00Z");
        var values = new ArrayList<EstimateRevisionEngine.Observation>();
        for (var type : EarningsEstimateResult.EstimateType.values()) {
            values.add(observation(type, "100", now.minusSeconds(91 * 86400L)));
            values.add(observation(type, "100", now.minusSeconds(31 * 86400L)));
            values.add(observation(type, "100", now.minusSeconds(8 * 86400L)));
            values.add(observation(type, "110", now));
        }

        var result = new EstimateRevisionEngine().evaluate(values, now);

        assertThat(result.day7()).isEqualTo(EstimateRevisionEngine.RevisionState.STRONGLY_POSITIVE);
        assertThat(result.day30()).isEqualTo(EstimateRevisionEngine.RevisionState.STRONGLY_POSITIVE);
        assertThat(result.day90()).isEqualTo(EstimateRevisionEngine.RevisionState.STRONGLY_POSITIVE);
        assertThat(result.epsDay30()).isEqualByComparingTo("0.1");
        assertThat(result.revenueDay90()).isEqualByComparingTo("0.1");
        assertThat(result.quality()).isEqualTo(ProviderModels.QualityStatus.HEALTHY);
    }

    private static EstimateRevisionEngine.Observation observation(
            EarningsEstimateResult.EstimateType type, String mean, Instant dataAsOf) {
        return new EstimateRevisionEngine.Observation(
                type,
                EarningsEstimateResult.PeriodType.ANNUAL,
                LocalDate.parse("2026-12-31"),
                "CURRENT_YEAR",
                new BigDecimal(mean),
                new BigDecimal(mean).multiply(new BigDecimal("1.1")),
                new BigDecimal(mean).multiply(new BigDecimal("0.9")),
                12,
                dataAsOf);
    }
}
