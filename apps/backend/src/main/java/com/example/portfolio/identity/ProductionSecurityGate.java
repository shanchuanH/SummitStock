package com.example.portfolio.identity;

import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("production")
final class ProductionSecurityGate implements SmartInitializingSingleton {
    private static final Set<String> FORBIDDEN_CREDENTIALS =
            Set.of("change-before-use", "change-local-app-password", "change-local-root-password");
    private final Environment environment;

    ProductionSecurityGate(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        requireCredential("portfolio.security.dev-password");
        requireCredential("spring.datasource.password");
        rejectPlaceholderIfPresent("MYSQL_ROOT_PASSWORD");
        if (!environment.getProperty("SESSION_COOKIE_SECURE", Boolean.class, false)) {
            throw new IllegalStateException("PRODUCTION_SESSION_COOKIE_MUST_BE_SECURE");
        }
        var forwarded = environment.getProperty("server.forward-headers-strategy", "");
        if (!List.of("framework", "native").contains(forwarded.toLowerCase())) {
            throw new IllegalStateException("PRODUCTION_FORWARDED_HEADERS_REQUIRED");
        }
        if (List.of(environment.getActiveProfiles()).contains("local-fixture")) {
            throw new IllegalStateException("PRODUCTION_LOCAL_FIXTURE_FORBIDDEN");
        }
        rejectFakeProvider("portfolio.providers.market.type");
        rejectFakeProvider("portfolio.providers.fundamentals.type");
    }

    private void requireCredential(String property) {
        var value = environment.getProperty(property, "");
        if (value.isBlank() || FORBIDDEN_CREDENTIALS.contains(value)) {
            throw new IllegalStateException("PRODUCTION_CREDENTIAL_REJECTED: " + property);
        }
    }

    private void rejectPlaceholderIfPresent(String property) {
        var value = environment.getProperty(property);
        if (value != null && FORBIDDEN_CREDENTIALS.contains(value)) {
            throw new IllegalStateException("PRODUCTION_CREDENTIAL_REJECTED: " + property);
        }
    }

    private void rejectFakeProvider(String property) {
        if ("fake".equalsIgnoreCase(environment.getProperty(property, ""))) {
            throw new IllegalStateException("PRODUCTION_FAKE_PROVIDER_FORBIDDEN: " + property);
        }
    }
}
