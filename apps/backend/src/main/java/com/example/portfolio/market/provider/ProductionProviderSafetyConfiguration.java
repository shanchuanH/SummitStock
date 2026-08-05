package com.example.portfolio.market.provider;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderProperties.class)
@Profile("!test & !local-fixture")
class ProductionProviderSafetyConfiguration {
    @Bean
    ProviderSafetyGate providerSafetyGate(ProviderProperties properties) {
        if (properties.mode() == ProviderProperties.Mode.DISABLED
                || properties.mode() == ProviderProperties.Mode.FAKE) {
            throw new IllegalStateException("Unsafe provider mode outside test/local-fixture: " + properties.mode());
        }
        return new ProviderSafetyGate(properties.mode());
    }

    record ProviderSafetyGate(ProviderProperties.Mode mode) {}
}
