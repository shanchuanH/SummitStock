package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.StrategyDefinition;
import com.example.portfolio.configuration.PortfolioProperties;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public final class PublishedStrategyService {
    private final StrategyDefinition definition;
    private final StrategyDefinitionLoader loader;
    private final JdbcClient jdbc;
    private final PortfolioProperties properties;

    public PublishedStrategyService(StrategyDefinitionLoader loader, PortfolioProperties properties, JdbcClient jdbc) {
        this.loader = loader;
        definition = loader.load(properties.strategyConfigPath());
        this.properties = properties;
        this.jdbc = jdbc;
        if (!definition.version().equals(properties.strategyVersion())) {
            throw new IllegalStateException("Runtime strategy version does not match published configuration");
        }
    }

    public StrategyDefinition current() {
        return definition;
    }

    public StrategyDefinition requireVersion(String version) {
        if (definition.version().equals(version)) {
            var registeredHash = jdbc.sql("SELECT config_hash FROM strategy_version WHERE version_code=:version")
                    .param("version", version)
                    .query(String.class)
                    .optional();
            if (registeredHash.isPresent() && !definition.configHash().equals(registeredHash.orElseThrow())) {
                throw new IllegalStateException("Run-bound strategy config hash does not match runtime definition");
            }
            return definition;
        }
        var persisted = jdbc.sql(
                        "SELECT CAST(config_json AS CHAR) configJson,config_hash configHash FROM strategy_version WHERE version_code=:version")
                .param("version", version)
                .query(PersistedDefinition.class)
                .optional()
                .orElseThrow(
                        () -> new IllegalStateException("Run-bound strategy definition is unavailable: " + version));
        return loader.loadPersistedJson(persisted.configJson(), persisted.configHash(), version);
    }

    public RuntimeStrategyStatus status() {
        Optional<DatabaseRelease> release = jdbc.sql(
                        """
                        SELECT status, config_hash configHash
                        FROM strategy_version
                        WHERE version_code=:version
                        """)
                .param("version", definition.version())
                .query(DatabaseRelease.class)
                .optional();
        var databaseStatus = release.map(DatabaseRelease::status).orElse("MISSING");
        var hashMatches = release.map(row -> definition.configHash().equals(row.configHash()))
                .orElse(false);
        var production = "PUBLISHED".equals(databaseStatus) && hashMatches;
        var draftOverride = properties.allowDraftStrategy()
                && "DRAFT".equals(definition.publishState())
                && (release.isEmpty() || "DRAFT".equals(databaseStatus))
                && (release.isEmpty() || hashMatches);
        var reason = production
                ? "PUBLISHED_STRATEGY_VERIFIED"
                : draftOverride
                        ? "DRAFT_STRATEGY_OVERRIDE"
                        : release.isEmpty()
                                ? "STRATEGY_VERSION_NOT_REGISTERED"
                                : !hashMatches ? "STRATEGY_CONFIG_HASH_MISMATCH" : "STRATEGY_NOT_PUBLISHED";
        return new RuntimeStrategyStatus(
                definition.version(),
                definition.configHash(),
                definition.publishState(),
                databaseStatus,
                production,
                draftOverride,
                reason);
    }

    public RuntimeStrategyStatus requireFormalRecommendationStrategy() {
        var status = status();
        if (!status.formalRecommendationsAllowed()) {
            throw new IllegalStateException("FORMAL_RECOMMENDATION_STRATEGY_REJECTED: " + status.reason());
        }
        return status;
    }

    private record DatabaseRelease(String status, String configHash) {}

    private record PersistedDefinition(String configJson, String configHash) {}

    public record RuntimeStrategyStatus(
            String version,
            String configHash,
            String yamlPublishState,
            String databaseStatus,
            boolean production,
            boolean draftOverride,
            String reason) {
        public boolean formalRecommendationsAllowed() {
            return production || draftOverride;
        }
    }
}
