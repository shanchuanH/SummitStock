package com.example.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;

class FlywayReleaseSafetyTest {
    @Test
    void upgradesAnExistingV52SchemaToTheReleaseHeadAndValidatesIt() {
        try (var mysql = new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("portfolio_upgrade_test")
                .withUsername("portfolio_test")
                .withPassword("portfolio_test")) {
            mysql.start();

            var v52 = flyway(mysql, MigrationVersion.fromVersion("52"));
            v52.migrate();
            assertThat(v52.info().current().getVersion().getVersion()).isEqualTo("52");

            var release = flyway(mysql, MigrationVersion.LATEST);
            var result = release.migrate();
            release.validate();

            assertThat(result.migrationsExecuted).isEqualTo(5);
            assertThat(release.info().current().getVersion().getVersion()).isEqualTo("57");
            assertThat(release.info().pending()).isEmpty();
        }
    }

    private static Flyway flyway(MySQLContainer<?> mysql, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .target(target)
                .load();
    }
}
