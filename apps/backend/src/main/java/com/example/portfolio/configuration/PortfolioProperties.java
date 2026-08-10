package com.example.portfolio.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("portfolio")
public record PortfolioProperties(
        @NotBlank String runtimeMode,
        @NotBlank String strategyVersion,
        @NotBlank String strategyConfigPath,
        @NotNull @Valid Security security) {

    public record Security(@NotBlank @Email String devUser, @NotBlank String devPassword) {}
}
