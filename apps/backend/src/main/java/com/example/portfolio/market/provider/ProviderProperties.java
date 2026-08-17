package com.example.portfolio.market.provider;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("portfolio.providers")
public record ProviderProperties(
        @Valid @NotNull Market market,
        @Valid @NotNull Fundamentals fundamentals,
        @Valid @NotNull Estimates estimates,
        @Valid @NotNull EarningsCalendar earningsCalendar,
        @Valid @NotNull Macro macro,
        boolean allowPartialProduction,
        @Valid @NotNull Execution execution) {

    @ConstructorBinding
    public ProviderProperties {}

    public ProviderProperties(Market market, Fundamentals fundamentals, Execution execution) {
        this(
                market,
                fundamentals,
                new Estimates(
                        EstimatesType.UNAVAILABLE,
                        "",
                        "",
                        Duration.ZERO,
                        execution.requestTimeout(),
                        execution.maxAttempts(),
                        execution.retryDelay()),
                new EarningsCalendar(
                        EarningsCalendarType.UNAVAILABLE,
                        "",
                        "",
                        Duration.ZERO,
                        execution.requestTimeout(),
                        execution.maxAttempts(),
                        execution.retryDelay()),
                new Macro(
                        MacroType.UNAVAILABLE,
                        "",
                        "",
                        Duration.ZERO,
                        execution.requestTimeout(),
                        execution.maxAttempts(),
                        execution.retryDelay()),
                false,
                execution);
    }

    public record Market(@NotNull MarketType type, @NotNull String baseUrl, @NotNull String apiKey) {}

    public record Fundamentals(@NotNull FundamentalsType type, @NotNull String baseUrl, @NotNull String userAgent) {}

    public record Estimates(
            @NotNull EstimatesType type,
            @NotNull String baseUrl,
            @NotNull String apiKey,
            @NotNull Duration minimumInterval,
            @NotNull Duration requestTimeout,
            @Min(1) int maxAttempts,
            @NotNull Duration retryDelay) {}

    public record EarningsCalendar(
            @NotNull EarningsCalendarType type,
            @NotNull String baseUrl,
            @NotNull String apiKey,
            @NotNull Duration minimumInterval,
            @NotNull Duration requestTimeout,
            @Min(1) int maxAttempts,
            @NotNull Duration retryDelay) {}

    public record Macro(
            @NotNull MacroType type,
            @NotNull String baseUrl,
            @NotNull String apiKey,
            @NotNull Duration minimumInterval,
            @NotNull Duration requestTimeout,
            @Min(1) int maxAttempts,
            @NotNull Duration retryDelay) {}

    public record Execution(
            @Min(1) int maxAttempts,
            @NotNull Duration retryDelay,
            @NotNull Duration minimumInterval,
            @NotNull Duration requestTimeout,
            @NotNull Duration connectTimeout) {}

    public enum MarketType {
        DISABLED,
        FAKE,
        ALPHA_VANTAGE,
        YAHOO
    }

    public enum FundamentalsType {
        DISABLED,
        FAKE,
        SEC
    }

    public enum EstimatesType {
        UNAVAILABLE,
        FAKE,
        ALPHA_VANTAGE
    }

    public enum EarningsCalendarType {
        UNAVAILABLE,
        FAKE,
        ALPHA_VANTAGE
    }

    public enum MacroType {
        UNAVAILABLE,
        FAKE,
        FRED
    }
}
