package com.example.portfolio.estimates;

import com.example.portfolio.market.provider.ProviderCallException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EstimateCollectionService {
    private static final Duration REFRESH_INTERVAL = Duration.ofDays(7);
    private final EstimateDataProvider provider;
    private final EstimateEvidenceStore store;
    private final Clock clock;

    public EstimateCollectionService(EstimateDataProvider provider, EstimateEvidenceStore store, Clock clock) {
        this.provider = provider;
        this.store = store;
        this.clock = clock;
    }

    public CollectionResult collectAll() {
        int observations = 0;
        int affected = 0;
        var failed = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var instrument : store.instrumentsNeedingRefresh(clock.instant().minus(REFRESH_INTERVAL))) {
            final EarningsEstimateResult result;
            try {
                result = provider.fetchEstimates(instrument.symbol());
            } catch (ProviderCallException exception) {
                failed.add(instrument.symbol());
                warnings.add(instrument.symbol() + ":" + exception.code());
                continue;
            }
            observations += result.estimates().size();
            affected += store.save(instrument.id(), result);
        }
        return new CollectionResult(observations, affected, failed, warnings);
    }

    public record CollectionResult(
            int observations, int affected, List<String> failedInstruments, List<String> warnings) {
        public CollectionResult {
            failedInstruments = List.copyOf(failedInstruments);
            warnings = List.copyOf(warnings);
        }

        public CollectionResult(int observations, int affected) {
            this(observations, affected, List.of(), List.of());
        }
    }
}
