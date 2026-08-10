package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.portfolio.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest(properties = "portfolio.allow-draft-strategy=false")
class StrategyRuntimePublicationGateTest extends MySqlIntegrationTest {
    @Autowired
    private PublishedStrategyService strategies;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    @AfterEach
    void removeRuntimeRelease() {
        jdbc.sql("DELETE FROM strategy_version WHERE version_code=:version")
                .param("version", strategies.current().version())
                .update();
    }

    @Test
    void missingOrDraftReleaseCannotCreateFormalRecommendations() {
        assertThatThrownBy(strategies::requireFormalRecommendationStrategy)
                .hasMessageContaining("STRATEGY_VERSION_NOT_REGISTERED");

        insertRelease("DRAFT", strategies.current().configHash());

        assertThatThrownBy(strategies::requireFormalRecommendationStrategy)
                .hasMessageContaining("STRATEGY_NOT_PUBLISHED");
    }

    @Test
    void publishedReleaseRequiresExactRuntimeConfigHash() {
        insertRelease("PUBLISHED", "0000000000000000000000000000000000000000000000000000000000000001");

        assertThatThrownBy(strategies::requireFormalRecommendationStrategy)
                .hasMessageContaining("STRATEGY_CONFIG_HASH_MISMATCH");
    }

    @Test
    void exactPublishedVersionAndHashAllowsFormalRecommendations() {
        insertRelease("PUBLISHED", strategies.current().configHash());

        var status = strategies.requireFormalRecommendationStrategy();

        assertThat(status.production()).isTrue();
        assertThat(status.formalRecommendationsAllowed()).isTrue();
        assertThat(status.databaseStatus()).isEqualTo("PUBLISHED");
    }

    private void insertRelease(String status, String configHash) {
        jdbc.sql(
                        """
                        INSERT INTO strategy_version
                            (id,version_code,status,config_json,config_hash,published_at,created_at)
                        VALUES
                            (UUID_TO_BIN(UUID()),:version,:status,JSON_OBJECT('runtimeGateTest',true),:hash,
                             IF(:status='PUBLISHED',UTC_TIMESTAMP(6),NULL),UTC_TIMESTAMP(6))
                        """)
                .param("version", strategies.current().version())
                .param("status", status)
                .param("hash", configHash)
                .update();
    }
}
