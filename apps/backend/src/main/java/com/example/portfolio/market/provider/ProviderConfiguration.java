package com.example.portfolio.market.provider;

import com.example.portfolio.market.persistence.ProviderRequestJournal;
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
    ProviderExecutionPolicy providerExecutionPolicy(
            Clock clock, ProviderProperties properties, ProviderRequestJournal journal) {
        var execution = properties.execution();
        return policy(
                clock,
                execution.maxAttempts(),
                execution.retryDelay(),
                execution.minimumInterval(),
                journal,
                properties.market().type() == ProviderProperties.MarketType.YAHOO ? "yahoo" : "alpha-vantage");
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
            ProviderExecutionPolicy providerExecutionPolicy,
            ProviderProperties properties,
            ObjectMapper json) {
        return new ProviderHttpClient(
                providerJavaHttpClient,
                providerExecutionPolicy,
                properties.execution().requestTimeout(),
                json);
    }

    @Bean("secProviderHttpClient")
    ProviderHttpClient secProviderHttpClient(
            HttpClient providerJavaHttpClient,
            Clock clock,
            ProviderProperties properties,
            ProviderRequestJournal journal,
            ObjectMapper json) {
        var execution = properties.execution();
        return new ProviderHttpClient(
                providerJavaHttpClient,
                policy(
                        clock,
                        execution.maxAttempts(),
                        execution.retryDelay(),
                        execution.minimumInterval(),
                        journal,
                        "sec"),
                execution.requestTimeout(),
                json);
    }

    @Bean("estimateProviderHttpClient")
    ProviderHttpClient estimateProviderHttpClient(
            HttpClient providerJavaHttpClient,
            Clock clock,
            ProviderProperties properties,
            ProviderRequestJournal journal,
            ObjectMapper json) {
        var config = properties.estimates();
        return client(
                providerJavaHttpClient,
                clock,
                json,
                config.maxAttempts(),
                config.retryDelay(),
                config.minimumInterval(),
                config.requestTimeout(),
                journal,
                "alpha-vantage-estimates");
    }

    @Bean("earningsCalendarProviderHttpClient")
    ProviderHttpClient earningsCalendarProviderHttpClient(
            HttpClient providerJavaHttpClient,
            Clock clock,
            ProviderProperties properties,
            ProviderRequestJournal journal,
            ObjectMapper json) {
        var config = properties.earningsCalendar();
        return client(
                providerJavaHttpClient,
                clock,
                json,
                config.maxAttempts(),
                config.retryDelay(),
                config.minimumInterval(),
                config.requestTimeout(),
                journal,
                "alpha-vantage-earnings");
    }

    @Bean("macroProviderHttpClient")
    ProviderHttpClient macroProviderHttpClient(
            HttpClient providerJavaHttpClient,
            Clock clock,
            ProviderProperties properties,
            ProviderRequestJournal journal,
            ObjectMapper json) {
        var config = properties.macro();
        return client(
                providerJavaHttpClient,
                clock,
                json,
                config.maxAttempts(),
                config.retryDelay(),
                config.minimumInterval(),
                config.requestTimeout(),
                journal,
                "fred");
    }

    private static ProviderHttpClient client(
            HttpClient http,
            Clock clock,
            ObjectMapper json,
            int maxAttempts,
            Duration retryDelay,
            Duration minimumInterval,
            Duration requestTimeout,
            ProviderRequestJournal journal,
            String providerId) {
        return new ProviderHttpClient(
                http,
                policy(clock, maxAttempts, retryDelay, minimumInterval, journal, providerId),
                requestTimeout,
                json);
    }

    private static ProviderExecutionPolicy policy(
            Clock clock,
            int maxAttempts,
            Duration retryDelay,
            Duration minimumInterval,
            ProviderRequestJournal journal,
            String providerId) {
        return new ProviderExecutionPolicy(
                clock, ProviderConfiguration::sleep, maxAttempts, retryDelay, minimumInterval, journal, providerId);
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
