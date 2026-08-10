package com.example.portfolio.analysis.narrative;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NarrativeFactValidatorTest {
    private final NarrativeFactValidator validator = new NarrativeFactValidator();

    @Test
    void rejectsNewPercentageTargetPriceAndUnsupportedAnalystConsensus() {
        var output = new DecisionNarrative(
                "持有", "分析师一致认为上涨 25%，目标价 200。", List.of("规则允许持有。"), List.of(), List.of(), "中等置信度");

        var result = validator.validate(input(), output);

        assertThat(result.valid()).isFalse();
        assertThat(result.violations())
                .contains("UNSUPPORTED_PERCENTAGE:25%", "UNSUPPORTED_TARGET_PRICE", "UNSUPPORTED_ANALYST_CONSENSUS");
    }

    @Test
    void acceptsOnlyFactsAlreadyPresentInStructuredInput() {
        var input = new NarrativeInput(
                "GOOGL",
                "QUALITY_STOCK",
                "DO_NOT_ADD",
                "STRONG",
                "FAIR",
                "POSITIVE",
                "WEAK",
                "BLOCKED",
                "QUALITY.NORMAL_MAX",
                List.of("仓位已达 12%。"),
                List.of("估值风险"),
                List.of("等待估值改善"),
                "HIGH",
                false);
        var output = new DecisionNarrative(
                "持有，暂不加仓", "仓位已达 12%。", List.of("仓位已达 12%。"), List.of("估值风险"), List.of("等待估值改善"), "高置信度");

        assertThat(validator.validate(input, output).valid()).isTrue();
    }

    private static NarrativeInput input() {
        return new NarrativeInput(
                "GOOGL",
                "QUALITY_STOCK",
                "HOLD",
                "STRONG",
                "FAIR",
                "POSITIVE",
                "WEAK",
                "AVAILABLE",
                "QUALITY.HOLD",
                List.of("当前规则为持有。"),
                List.of(),
                List.of(),
                "MEDIUM",
                false);
    }
}
