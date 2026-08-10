package com.example.portfolio.analysis.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NarrativeGeneratorTest {
    private final NarrativeInput input = new NarrativeInput(
            "GOOGL",
            "QUALITY_STOCK",
            "DO_NOT_ADD",
            "STRONG",
            "FAIR",
            "POSITIVE",
            "WEAK",
            "BLOCKED",
            "QUALITY.NORMAL_MAX",
            List.of("仓位容量被阻止。"),
            List.of("集中度风险"),
            List.of("等待容量恢复"),
            "HIGH",
            false);

    @Test
    void unavailableModelUsesDeterministicTemplateWithoutBlockingRecommendation() {
        var generator = generator(value -> Optional.empty());

        var result = generator.generate(input);

        assertThat(result.source()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(result.validationStatus()).isEqualTo("MODEL_UNAVAILABLE");
        assertThat(result.narrative().headline()).isEqualTo("持有，暂不加仓");
    }

    @Test
    void invalidModelFactsAreRejectedAndReplaced() {
        var invalid = new DecisionNarrative("持有", "目标价 250，上涨 30%。", List.of(), List.of(), List.of(), "高置信度");
        var generator = generator(
                value -> Optional.of(new NarrativeModelClient.ModelNarrative(invalid, "test-provider", "test-model")));

        var result = generator.generate(input);

        assertThat(result.source()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(result.validationStatus()).startsWith("REJECTED:");
        assertThat(result.narrative().text()).doesNotContain("250", "30%");
    }

    private static NarrativeGenerator generator(NarrativeModelClient client) {
        return new NarrativeGenerator(client, new NarrativeFactValidator(), new DeterministicNarrativeTemplate());
    }
}
