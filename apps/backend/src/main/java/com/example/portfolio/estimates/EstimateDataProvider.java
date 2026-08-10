package com.example.portfolio.estimates;

public interface EstimateDataProvider {
    EarningsEstimateResult fetchEstimates(String symbol);
}
