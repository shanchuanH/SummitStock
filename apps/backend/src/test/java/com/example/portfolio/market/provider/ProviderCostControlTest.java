package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderCostControlTest {
    @Test
    void keepsCriticalOwnedHoldingEvidenceWhenOptionalWorkIsPaused() {
        assertThat(ProviderCostControl.decide(90, 100, ProviderCostControl.Priority.P0)
                        .allowed())
                .isTrue();
        assertThat(ProviderCostControl.decide(100, 100, ProviderCostControl.Priority.P1)
                        .allowed())
                .isTrue();
        assertThat(ProviderCostControl.decide(90, 100, ProviderCostControl.Priority.P3)
                        .allowed())
                .isFalse();
        assertThat(ProviderCostControl.decide(90, 100, ProviderCostControl.Priority.P4)
                        .reason())
                .isEqualTo("OPTIONAL_WORK_PAUSED");
    }

    @Test
    void maps_collection_cadence_to_explicit_priority() {
        assertThat(ProviderCostControl.priority("daily-bars")).isEqualTo(ProviderCostControl.Priority.P0);
        assertThat(ProviderCostControl.priority("company-fundamentals")).isEqualTo(ProviderCostControl.Priority.P1);
        assertThat(ProviderCostControl.priority("etf-profile")).isEqualTo(ProviderCostControl.Priority.P2);
        assertThat(ProviderCostControl.priority("watchlist-refresh")).isEqualTo(ProviderCostControl.Priority.P3);
        assertThat(ProviderCostControl.priority("news")).isEqualTo(ProviderCostControl.Priority.P4);
    }
}
