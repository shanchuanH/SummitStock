package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProviderPlanLimitDoesNotBecomeNoActionTest {
    @Test
    void planLimitRemainsAnExplicitNonRetryableProviderFailure() throws Exception {
        var payload = new ObjectMapper().readTree("{\"Note\":\"This endpoint requires a premium plan\"}");

        assertThatThrownBy(() -> ProviderPayloads.rejectProviderError(payload))
                .isInstanceOf(ProviderCallException.class)
                .satisfies(error -> {
                    var providerError = (ProviderCallException) error;
                    org.assertj.core.api.Assertions.assertThat(providerError.errorCode())
                            .isEqualTo(ProviderErrorCode.PROVIDER_PLAN_LIMIT);
                    org.assertj.core.api.Assertions.assertThat(providerError.retryable())
                            .isFalse();
                });
    }
}
