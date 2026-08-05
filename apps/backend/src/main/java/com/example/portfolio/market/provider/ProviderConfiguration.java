package com.example.portfolio.market.provider;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderProperties.class)
class ProviderConfiguration {
    @Bean
    ProviderExecutor providerExecutor(Clock clock, ProviderProperties properties) {
        var execution = properties.execution();
        return new ProviderExecutor(
                clock,
                ProviderConfiguration::sleep,
                execution.maxAttempts(),
                execution.retryDelay(),
                execution.minimumInterval());
    }

    @Bean
    HttpClient providerJavaHttpClient(ProviderProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.execution().connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean
    ProviderHttpClient providerHttpClient(
            HttpClient providerJavaHttpClient,
            ProviderExecutor providerExecutor,
            ProviderProperties properties,
            ObjectMapper json) {
        return new ProviderHttpClient(
                providerJavaHttpClient, providerExecutor, properties.execution().requestTimeout(), json);
    }

    private static void sleep(java.time.Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Provider wait interrupted", exception);
        }
    }
}
