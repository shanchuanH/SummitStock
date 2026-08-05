package com.example.portfolio.market.provider;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ProviderConfiguration {
    @Bean
    ProviderExecutor providerExecutor(Clock clock) {
        return new ProviderExecutor(
                clock, ProviderConfiguration::sleep, 3, Duration.ofMillis(100), Duration.ofMillis(50));
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider wait interrupted", exception);
        }
    }
}
