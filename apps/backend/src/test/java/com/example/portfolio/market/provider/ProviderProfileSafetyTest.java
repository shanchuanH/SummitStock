package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProviderProfileSafetyTest {
    @Test
    void productionProfileNeverLoadsFakeProviders() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                .withUserConfiguration(FakeEodMarketDataProvider.class, FakeSecFundamentalsProvider.class)
                .run(context -> {
                    assertThat(context.getBeansOfType(MarketDataProvider.class)).isEmpty();
                    assertThat(context.getBeansOfType(FundamentalsProvider.class))
                            .isEmpty();
                });
    }

    @Test
    void localFixtureProfileLoadsFakeProviders() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-fixture"))
                .withUserConfiguration(FakeEodMarketDataProvider.class, FakeSecFundamentalsProvider.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(MarketDataProvider.class);
                    assertThat(context).hasSingleBean(FundamentalsProvider.class);
                });
    }

    @Test
    void unsafeProviderModesFailOutsideFixtureProfiles() {
        for (String mode : new String[] {"disabled", "fake"}) {
            new ApplicationContextRunner()
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                    .withUserConfiguration(ProductionProviderSafetyConfiguration.class)
                    .withPropertyValues("portfolio.providers.mode=" + mode)
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .hasMessageContaining("Unsafe provider mode");
                    });
        }
    }
}
