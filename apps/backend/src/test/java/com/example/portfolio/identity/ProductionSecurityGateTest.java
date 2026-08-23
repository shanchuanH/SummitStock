package com.example.portfolio.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

class ProductionSecurityGateTest {
    @Test
    void productionProfileDisablesPublicApiDocumentation() throws Exception {
        var sources =
                new YamlPropertySourceLoader().load("production", new ClassPathResource("application-production.yaml"));

        assertThat(sources).anySatisfy(source -> {
            assertThat(source.getProperty("springdoc.api-docs.enabled")).isEqualTo(false);
            assertThat(source.getProperty("springdoc.swagger-ui.enabled")).isEqualTo(false);
        });
    }

    @Test
    void rejectsEveryDocumentedPlaceholderCredential() {
        assertRejected("portfolio.security.dev-password=change-before-use", "portfolio.security.dev-password");
        assertRejected("spring.datasource.password=change-local-app-password", "spring.datasource.password");
        assertRejected("MYSQL_ROOT_PASSWORD=change-local-root-password", "MYSQL_ROOT_PASSWORD");
    }

    @Test
    void rejectsInsecureCookiesMissingProxyHandlingAndFakeProviders() {
        assertRejected("SESSION_COOKIE_SECURE=false", "PRODUCTION_SESSION_COOKIE_MUST_BE_SECURE");
        assertRejected("server.forward-headers-strategy=none", "PRODUCTION_FORWARDED_HEADERS_REQUIRED");
        assertRejected("portfolio.providers.market.type=fake", "PRODUCTION_FAKE_PROVIDER_FORBIDDEN");
        assertRejected("portfolio.providers.fundamentals.type=fake", "PRODUCTION_FAKE_PROVIDER_FORBIDDEN");
        assertRejected("springdoc.api-docs.enabled=true", "PRODUCTION_API_DOCS_MUST_BE_DISABLED");
    }

    @Test
    void acceptsExplicitNonPlaceholderCredentialsTlsAndForwardedHeaders() {
        runner().run(context -> assertThat(context).hasNotFailed());
    }

    private static void assertRejected(String override, String message) {
        runner().withPropertyValues(override).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasMessageContaining(message);
        });
    }

    private static ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("production"))
                .withUserConfiguration(ProductionSecurityGate.class)
                .withPropertyValues(
                        "portfolio.security.dev-password=a-strong-runtime-secret",
                        "spring.datasource.password=a-distinct-database-secret",
                        "MYSQL_ROOT_PASSWORD=a-distinct-root-secret",
                        "SESSION_COOKIE_SECURE=true",
                        "server.forward-headers-strategy=framework",
                        "portfolio.providers.market.type=alpha-vantage",
                        "portfolio.providers.fundamentals.type=sec");
    }
}
