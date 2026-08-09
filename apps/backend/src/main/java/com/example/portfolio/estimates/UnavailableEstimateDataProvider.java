package com.example.portfolio.estimates;

import com.example.portfolio.market.provider.ProviderModels;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public final class UnavailableEstimateDataProvider implements EstimateDataProvider {
    private final Clock clock;

    public UnavailableEstimateDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public EarningsEstimateResult fetchEstimates(String symbol) {
        return new EarningsEstimateResult(
                symbol, List.of(), "unavailable", clock.instant(), ProviderModels.QualityStatus.MISSING);
    }
}
