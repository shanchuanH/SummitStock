package com.example.portfolio.market.provider;

import java.util.ArrayList;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderProperties.class)
@Profile("!test & !local-fixture")
class ProductionProviderSafetyConfiguration {
    @Bean
    ProviderRuntimeStatus providerSafetyGate(ProviderProperties properties) {
        var unavailable = new ArrayList<String>();
        var market = properties.market();
        var fundamentals = properties.fundamentals();
        if (market.type() == ProviderProperties.MarketType.DISABLED
                || market.type() == ProviderProperties.MarketType.FAKE) {
            unavailable.add("market");
        }
        if (market.type() == ProviderProperties.MarketType.ALPHA_VANTAGE
                && (!StringUtils.hasText(market.baseUrl()) || !StringUtils.hasText(market.apiKey()))) {
            throw new IllegalStateException("Production market provider requires base URL and API key");
        }
        if (fundamentals.type() == ProviderProperties.FundamentalsType.DISABLED
                || fundamentals.type() == ProviderProperties.FundamentalsType.FAKE) {
            unavailable.add("fundamentals");
        }
        if (fundamentals.type() == ProviderProperties.FundamentalsType.SEC
                && (!StringUtils.hasText(fundamentals.baseUrl()) || !validSecUserAgent(fundamentals.userAgent()))) {
            throw new IllegalStateException(
                    "SEC provider requires a declared organization and contact email User-Agent");
        }
        validateExternal(
                "estimates",
                properties.estimates().type() == ProviderProperties.EstimatesType.ALPHA_VANTAGE,
                properties.estimates().type() == ProviderProperties.EstimatesType.UNAVAILABLE
                        || properties.estimates().type() == ProviderProperties.EstimatesType.FAKE,
                properties.estimates().baseUrl(),
                properties.estimates().apiKey(),
                unavailable);
        validateExternal(
                "earnings-calendar",
                properties.earningsCalendar().type() == ProviderProperties.EarningsCalendarType.ALPHA_VANTAGE,
                properties.earningsCalendar().type() == ProviderProperties.EarningsCalendarType.UNAVAILABLE
                        || properties.earningsCalendar().type() == ProviderProperties.EarningsCalendarType.FAKE,
                properties.earningsCalendar().baseUrl(),
                properties.earningsCalendar().apiKey(),
                unavailable);
        validateExternal(
                "macro",
                properties.macro().type() == ProviderProperties.MacroType.FRED,
                properties.macro().type() == ProviderProperties.MacroType.UNAVAILABLE
                        || properties.macro().type() == ProviderProperties.MacroType.FAKE,
                properties.macro().baseUrl(),
                properties.macro().apiKey(),
                unavailable);
        if (!unavailable.isEmpty() && !properties.allowPartialProduction()) {
            throw new IllegalStateException(
                    "Formal production providers are unavailable: " + String.join(", ", unavailable)
                            + "; set ALLOW_PARTIAL_PRODUCTION=true only for an explicitly partial deployment");
        }
        return unavailable.isEmpty()
                ? ProviderRuntimeStatus.complete()
                : new ProviderRuntimeStatus("PARTIAL", unavailable);
    }

    private static void validateExternal(
            String name,
            boolean selected,
            boolean unavailable,
            String baseUrl,
            String apiKey,
            ArrayList<String> missing) {
        if (unavailable) missing.add(name);
        if (selected && (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(apiKey))) {
            throw new IllegalStateException("Production " + name + " provider requires base URL and API key");
        }
    }

    static boolean validSecUserAgent(String value) {
        return StringUtils.hasText(value)
                && value.strip().contains(" ")
                && value.matches(".*[^\\s@]+@[^\\s@]+\\.[^\\s@]+.*");
    }
}
