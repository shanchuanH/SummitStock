package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionProviderSafetyTest {
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
    void unsafeOrIncompleteProvidersFailOutsideFixtureProfiles() {
        assertStartupFails("disabled", "sec", "market provider");
        assertStartupFails("fake", "sec", "market provider");
        assertStartupFails("alpha-vantage", "fake", "fundamentals provider");
        assertStartupFails("alpha-vantage", "disabled", "fundamentals provider");
        assertStartupFails("alpha-vantage", "sec", "User-Agent", "portfolio.providers.fundamentals.user-agent=");
        assertStartupFails("alpha-vantage", "sec", "API key", "portfolio.providers.market.api-key=");
    }

    @Test
    void completeRealProviderConfigurationPassesGate() {
        runner("alpha-vantage", "sec").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProductionProviderSafetyConfiguration.ProviderSafetyGate.class);
        });
    }

    private static void assertStartupFails(String market, String fundamentals, String message, String... override) {
        var runner = runner(market, fundamentals);
        if (override.length > 0) runner = runner.withPropertyValues(override);
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining(message);
        });
    }

    private static ApplicationContextRunner runner(String market, String fundamentals) {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                .withUserConfiguration(ProductionProviderSafetyConfiguration.class)
                .withPropertyValues(
                        "portfolio.providers.market.type=" + market,
                        "portfolio.providers.market.base-url=https://example.test/query",
                        "portfolio.providers.market.api-key=secret",
                        "portfolio.providers.fundamentals.type=" + fundamentals,
                        "portfolio.providers.fundamentals.base-url=https://data.sec.gov",
                        "portfolio.providers.fundamentals.user-agent=SummitStock ops@example.test",
                        "portfolio.providers.execution.max-attempts=3",
                        "portfolio.providers.execution.retry-delay=1ms",
                        "portfolio.providers.execution.minimum-interval=0ms",
                        "portfolio.providers.execution.request-timeout=1s",
                        "portfolio.providers.execution.connect-timeout=1s");
    }
}
