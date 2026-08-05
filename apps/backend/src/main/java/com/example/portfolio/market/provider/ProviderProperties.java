package com.example.portfolio.market.provider;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("portfolio.providers")
public record ProviderProperties(@NotNull Mode mode) {
    public enum Mode {
        DISABLED,
        FAKE,
        PRODUCTION
    }
}
