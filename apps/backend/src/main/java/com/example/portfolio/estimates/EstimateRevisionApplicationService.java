package com.example.portfolio.estimates;

import java.time.Clock;
import org.springframework.stereotype.Service;

@Service
public class EstimateRevisionApplicationService {
    private final EstimateEvidenceStore store;
    private final Clock clock;
    private final EstimateRevisionEngine engine = new EstimateRevisionEngine();

    public EstimateRevisionApplicationService(EstimateEvidenceStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public int computeAll() {
        int affected = 0;
        for (var instrument : store.eligibleInstruments()) {
            affected += store.saveRevision(
                    instrument.id(), engine.evaluate(store.observations(instrument.id()), clock.instant()));
        }
        return affected;
    }
}
