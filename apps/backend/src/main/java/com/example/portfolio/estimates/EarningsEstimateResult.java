package com.example.portfolio.estimates;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record EarningsEstimateResult(
        String symbol,
        List<Estimate> estimates,
        String source,
        Instant dataAsOf,
        ProviderModels.QualityStatus quality) {
    public EarningsEstimateResult {
        estimates = List.copyOf(estimates);
    }

    public record Estimate(
            EstimateType estimateType,
            PeriodType periodType,
            LocalDate periodEnd,
            String horizon,
            BigDecimal mean,
            BigDecimal high,
            BigDecimal low,
            Integer analystCount) {}

    public enum EstimateType {
        EPS,
        REVENUE
    }

    public enum PeriodType {
        QUARTERLY,
        ANNUAL
    }
}
