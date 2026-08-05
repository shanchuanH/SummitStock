package com.example.portfolio.market.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("portfolio.providers")
public record ProviderProperties(
        @Valid @NotNull Market market, @Valid @NotNull Fundamentals fundamentals, @Valid @NotNull Execution execution) {

    public record Market(@NotNull MarketType type, @NotNull String baseUrl, @NotNull String apiKey) {}

    public record Fundamentals(@NotNull FundamentalsType type, @NotNull String baseUrl, @NotNull String userAgent) {}

    public record Execution(
            @Min(1) int maxAttempts,
            @NotNull Duration retryDelay,
            @NotNull Duration minimumInterval,
            @NotNull Duration requestTimeout,
            @NotNull Duration connectTimeout) {}

    public enum MarketType {
        DISABLED,
        FAKE,
        ALPHA_VANTAGE
    }

    public enum FundamentalsType {
        DISABLED,
        FAKE,
        SEC
    }
}
