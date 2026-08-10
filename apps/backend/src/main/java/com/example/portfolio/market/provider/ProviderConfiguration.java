package com.example.portfolio.market.provider;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
    @Primary
    ProviderHttpClient providerHttpClient(
            HttpClient providerJavaHttpClient,
            ProviderExecutor providerExecutor,
            ProviderProperties properties,
            ObjectMapper json) {
        return new ProviderHttpClient(
                providerJavaHttpClient, providerExecutor, properties.execution().requestTimeout(), json);
    }

    @Bean("estimateProviderHttpClient")
    ProviderHttpClient estimateProviderHttpClient(
            HttpClient providerJavaHttpClient, Clock clock, ProviderProperties properties, ObjectMapper json) {
        var config = properties.estimates();
        return client(
                providerJavaHttpClient,
                clock,
                json,
                config.maxAttempts(),
                config.retryDelay(),
                config.minimumInterval(),
                config.requestTimeout());
    }

    @Bean("earningsCalendarProviderHttpClient")
    ProviderHttpClient earningsCalendarProviderHttpClient(
            HttpClient providerJavaHttpClient, Clock clock, ProviderProperties properties, ObjectMapper json) {
        var config = properties.earningsCalendar();
        return client(
                providerJavaHttpClient,
                clock,
                json,
                config.maxAttempts(),
                config.retryDelay(),
                config.minimumInterval(),
                config.requestTimeout());
    }

    @Bean("macroProviderHttpClient")
    ProviderHttpClient macroProviderHttpClient(
            HttpClient providerJavaHttpClient, Clock clock, ProviderProperties properties, ObjectMapper json) {
        var config = properties.macro();
        return client(
                providerJavaHttpClient,
                clock,
                json,
                config.maxAttempts(),
                config.retryDelay(),
                config.minimumInterval(),
                config.requestTimeout());
    }

    private static ProviderHttpClient client(
            HttpClient http,
            Clock clock,
            ObjectMapper json,
            int maxAttempts,
            Duration retryDelay,
            Duration minimumInterval,
            Duration requestTimeout) {
        return new ProviderHttpClient(
                http,
                new ProviderExecutor(clock, ProviderConfiguration::sleep, maxAttempts, retryDelay, minimumInterval),
                requestTimeout,
                json);
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
