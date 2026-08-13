package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;

class StrategyV3PublicationValidationTest {
    private static final String CONFIG = "strategy/STRATEGY_CONFIG_V3_DRAFT.yaml";

    @Test
    void canonicalV3SatisfiesEveryPublicationInvariant() {
        var strategy =
                new StrategyDefinitionLoader(new org.springframework.core.io.DefaultResourceLoader()).load(CONFIG);

        assertThat(strategy.version()).isEqualTo("3.0.0-draft");
        assertThat(strategy.sleeveAllocations().total()).isEqualByComparingTo("1.00");
        assertThat(strategy.quality().hardMax()).isEqualByComparingTo("0.15");
        assertThat(strategy.absoluteTradeRiskMax()).isEqualByComparingTo("0.0050");
        assertThat(strategy.totalOpenRiskMax()).isEqualByComparingTo("0.0200");
        assertThat(strategy.clusterOpenRiskMax()).isEqualByComparingTo("0.0075");
        assertThat(strategy.speculativeTimeStopTradingDays()).isEqualTo(25);
        assertThat(strategy.maxDailyMustAct()).isEqualTo(3);
        assertThat(strategy.manualExecutionOnly()).isTrue();
    }

    @Test
    void invalidAllocationRiskDrawdownTranchesMustActAndExecutionModeFailClosed() throws IOException {
        var yaml = new String(new ClassPathResource(CONFIG).getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        rejects(yaml.replace("broadUsCore: \"0.35\"", "broadUsCore: \"0.34\""), "sum to 100%");
        rejects(yaml.replace("tradeRiskPct: \"0.004\"", "tradeRiskPct: \"0.006\""), "Trade risk");
        rejects(yaml.replace("clusterOpenRiskMax: \"0.0075\"", "clusterOpenRiskMax: \"0.025\""), "Cluster risk");
        rejects(yaml.replace("etfDipSetupAt: \"0.12\"", "etfDipSetupAt: \"0.09\""), "strictly increasing");
        rejects(yaml.replace("\"0.30\", \"0.25\"", "\"0.20\", \"0.25\""), "sum to 100%");
        rejects(yaml.replace("maxDailyMustAct: 3", "maxDailyMustAct: 4"), "cannot exceed three");
        rejects(yaml.replace("manualExecutionOnly: true", "manualExecutionOnly: false"), "manual");
    }

    private static void rejects(String yaml, String message) {
        var loader = new StrategyDefinitionLoader(new ResourceLoader() {
            @Override
            public org.springframework.core.io.Resource getResource(String ignored) {
                return new ByteArrayResource(yaml.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public ClassLoader getClassLoader() {
                return StrategyV3PublicationValidationTest.class.getClassLoader();
            }
        });
        assertThatThrownBy(() -> loader.load("mutated.yaml")).hasMessageContaining(message);
    }
}
