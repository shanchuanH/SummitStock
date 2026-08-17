package com.example.portfolio.estimates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.portfolio.market.provider.ProviderCallException;
import com.example.portfolio.market.provider.ProviderModels;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EstimateCollectionServiceIsolationTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void oneTickerFailureProducesPartialResultAndContinuesOtherTickers() {
        var provider = mock(EstimateDataProvider.class);
        var store = mock(EstimateEvidenceStore.class);
        var failed = new EstimateEvidenceStore.InstrumentRef(UUID.randomUUID(), "FAIL");
        var healthy = new EstimateEvidenceStore.InstrumentRef(UUID.randomUUID(), "GOOD");
        var result = new EarningsEstimateResult(
                "GOOD",
                List.of(),
                "provider",
                Instant.parse("2026-08-05T00:00:00Z"),
                ProviderModels.QualityStatus.HEALTHY);
        when(store.instrumentsNeedingRefresh(Instant.parse("2026-08-05T00:00:00Z")))
                .thenReturn(List.of(failed, healthy));
        when(provider.fetchEstimates("FAIL"))
                .thenThrow(new ProviderCallException("PROVIDER_TIMEOUT", "timeout", null, true));
        when(provider.fetchEstimates("GOOD")).thenReturn(result);

        var collected = new EstimateCollectionService(provider, store, CLOCK).collectAll();

        assertThat(collected.failedInstruments()).containsExactly("FAIL");
        assertThat(collected.warnings()).containsExactly("FAIL:PROVIDER_TIMEOUT");
        verify(store).save(healthy.id(), result);
    }
}
