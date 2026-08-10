package com.example.portfolio.market.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.earnings.UnavailableEarningsCalendarProvider;
import com.example.portfolio.estimates.UnavailableEstimateDataProvider;
import com.example.portfolio.macro.UnavailableMacroDataProvider;
import java.time.Clock;
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
        assertStartupFails("disabled", "sec", "market");
        assertStartupFails("fake", "sec", "market");
        assertStartupFails("alpha-vantage", "fake", "fundamentals");
        assertStartupFails("alpha-vantage", "disabled", "fundamentals");
        assertStartupFails("alpha-vantage", "sec", "User-Agent", "portfolio.providers.fundamentals.user-agent=");
        assertStartupFails("alpha-vantage", "sec", "API key", "portfolio.providers.market.api-key=");
    }

    @Test
    void completeRealProviderConfigurationPassesGate() {
        runner("alpha-vantage", "sec").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ProviderRuntimeStatus.class);
            assertThat(context.getBean(ProviderRuntimeStatus.class).status()).isEqualTo("COMPLETE");
        });
    }

    @Test
    void explicitlyAllowedIncompleteProductionIsReportedPartial() {
        runner("alpha-vantage", "sec")
                .withPropertyValues(
                        "portfolio.providers.estimates.type=unavailable",
                        "portfolio.providers.allow-partial-production=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ProviderRuntimeStatus.class).status())
                            .isEqualTo("PARTIAL");
                    assertThat(context.getBean(ProviderRuntimeStatus.class).unavailableProviders())
                            .containsExactly("estimates");
                });
    }

    @Test
    void partialProductionUsesUnavailableAdaptersInsteadOfFakeEvidence() {
        runner("disabled", "disabled")
                .withPropertyValues(
                        "portfolio.providers.estimates.type=unavailable",
                        "portfolio.providers.earnings-calendar.type=unavailable",
                        "portfolio.providers.macro.type=unavailable",
                        "portfolio.providers.allow-partial-production=true")
                .withBean(Clock.class, Clock::systemUTC)
                .withUserConfiguration(
                        UnavailableMarketDataProvider.class,
                        UnavailableFundamentalsProvider.class,
                        UnavailableEstimateDataProvider.class,
                        UnavailableEarningsCalendarProvider.class,
                        UnavailableMacroDataProvider.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ProviderRuntimeStatus.class).status())
                            .isEqualTo("PARTIAL");
                    assertThat(context).hasSingleBean(MarketDataProvider.class);
                    assertThat(context).hasSingleBean(FundamentalsProvider.class);
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
                        "portfolio.providers.estimates.type=alpha-vantage",
                        "portfolio.providers.estimates.base-url=https://example.test/query",
                        "portfolio.providers.estimates.api-key=secret",
                        "portfolio.providers.estimates.minimum-interval=1ms",
                        "portfolio.providers.estimates.request-timeout=1s",
                        "portfolio.providers.estimates.max-attempts=2",
                        "portfolio.providers.estimates.retry-delay=1ms",
                        "portfolio.providers.earnings-calendar.type=alpha-vantage",
                        "portfolio.providers.earnings-calendar.base-url=https://example.test/query",
                        "portfolio.providers.earnings-calendar.api-key=secret",
                        "portfolio.providers.earnings-calendar.minimum-interval=1ms",
                        "portfolio.providers.earnings-calendar.request-timeout=1s",
                        "portfolio.providers.earnings-calendar.max-attempts=2",
                        "portfolio.providers.earnings-calendar.retry-delay=1ms",
                        "portfolio.providers.macro.type=fred",
                        "portfolio.providers.macro.base-url=https://example.test/fred",
                        "portfolio.providers.macro.api-key=secret",
                        "portfolio.providers.macro.minimum-interval=1ms",
                        "portfolio.providers.macro.request-timeout=1s",
                        "portfolio.providers.macro.max-attempts=2",
                        "portfolio.providers.macro.retry-delay=1ms",
                        "portfolio.providers.allow-partial-production=false",
                        "portfolio.providers.execution.max-attempts=3",
                        "portfolio.providers.execution.retry-delay=1ms",
                        "portfolio.providers.execution.minimum-interval=0ms",
                        "portfolio.providers.execution.request-timeout=1s",
                        "portfolio.providers.execution.connect-timeout=1s");
    }
}
