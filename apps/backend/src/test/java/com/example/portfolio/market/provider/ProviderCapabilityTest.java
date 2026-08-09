package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderCapabilityTest {
    @Test
    void providersExposeTheirActualCapabilities() {
        MarketDataProvider market = new FakeEodMarketDataProvider();
        FundamentalsProvider fundamentals = new FakeSecFundamentalsProvider();

        assertThat(market.capabilities())
                .contains(
                        ProviderCapability.EOD_BARS,
                        ProviderCapability.ADJUSTED_BARS,
                        ProviderCapability.QUOTE_LAST,
                        ProviderCapability.QUOTE_BID_ASK,
                        ProviderCapability.CORPORATE_ACTIONS)
                .doesNotContain(ProviderCapability.FUNDAMENTALS);
        assertThat(fundamentals.capabilities()).containsExactly(ProviderCapability.FUNDAMENTALS);
        assertThat(fundamentals.providerId()).isEqualTo("fake-sec-ir");
    }
}
