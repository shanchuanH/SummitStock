package com.example.portfolio.estimates;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Deterministic demo evidence. This provider is never active outside local-fixture. */
@Component
@Primary
@Profile("local-fixture")
@ConditionalOnProperty(name = "portfolio.providers.estimates.type", havingValue = "fake")
final class FakeEstimateDataProvider implements EstimateDataProvider {
    private final Clock clock;

    FakeEstimateDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public EarningsEstimateResult fetchEstimates(String symbol) {
        var today = LocalDate.now(clock);
        var annualEnd = LocalDate.of(today.getYear() + 1, 12, 31);
        var seed = Math.floorMod(symbol.hashCode(), 50);
        var eps = new BigDecimal("4.00").add(BigDecimal.valueOf(seed).movePointLeft(1));
        var revenue = new BigDecimal("10000000000").add(BigDecimal.valueOf(seed).multiply(new BigDecimal("100000000")));
        return new EarningsEstimateResult(
                symbol,
                List.of(
                        new EarningsEstimateResult.Estimate(
                                EarningsEstimateResult.EstimateType.EPS,
                                EarningsEstimateResult.PeriodType.ANNUAL,
                                annualEnd,
                                "FY1",
                                eps,
                                eps.multiply(new BigDecimal("1.08")),
                                eps.multiply(new BigDecimal("0.92")),
                                24),
                        new EarningsEstimateResult.Estimate(
                                EarningsEstimateResult.EstimateType.REVENUE,
                                EarningsEstimateResult.PeriodType.ANNUAL,
                                annualEnd,
                                "FY1",
                                revenue,
                                revenue.multiply(new BigDecimal("1.06")),
                                revenue.multiply(new BigDecimal("0.94")),
                                22)),
                "fixture-estimates",
                today.atStartOfDay().toInstant(ZoneOffset.UTC),
                ProviderModels.QualityStatus.HEALTHY);
    }
}
