package com.example.portfolio.market.provider;

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
    ProviderSafetyGate providerSafetyGate(ProviderProperties properties) {
        var market = properties.market();
        var fundamentals = properties.fundamentals();
        if (market.type() == ProviderProperties.MarketType.DISABLED
                || market.type() == ProviderProperties.MarketType.FAKE) {
            throw new IllegalStateException("Unsafe market provider outside test/local-fixture: " + market.type());
        }
        if (!StringUtils.hasText(market.baseUrl()) || !StringUtils.hasText(market.apiKey())) {
            throw new IllegalStateException("Production market provider requires base URL and API key");
        }
        if (fundamentals.type() == ProviderProperties.FundamentalsType.DISABLED
                || fundamentals.type() == ProviderProperties.FundamentalsType.FAKE) {
            throw new IllegalStateException(
                    "Unsafe fundamentals provider outside test/local-fixture: " + fundamentals.type());
        }
        if (!StringUtils.hasText(fundamentals.baseUrl()) || !validSecUserAgent(fundamentals.userAgent())) {
            throw new IllegalStateException(
                    "SEC provider requires a declared organization and contact email User-Agent");
        }
        return new ProviderSafetyGate(market.type(), fundamentals.type());
    }

    static boolean validSecUserAgent(String value) {
        return StringUtils.hasText(value)
                && value.strip().contains(" ")
                && value.matches(".*[^\\s@]+@[^\\s@]+\\.[^\\s@]+.*");
    }

    record ProviderSafetyGate(
            ProviderProperties.MarketType marketType, ProviderProperties.FundamentalsType fundamentalsType) {}
}
