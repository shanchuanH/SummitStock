package com.example.portfolio.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.market.MarketRegimeEngine;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalMarketRegimeEvidenceTest {
    private static final Instant DATA_AS_OF = Instant.parse("2026-08-20T20:00:00Z");

    @Test
    void canonicalJsonContainsEveryRegimeAndHardOverrideInputInFixedOrder() {
        var json = CanonicalMarketRegimeEvidence.from(baseline()).canonicalJson();

        assertThat(json)
                .isEqualTo("{\"trend\":0.8,\"momentum\":0.7,\"breadth\":0.6,\"stressResilience\":0.9,"
                        + "\"spyBelow200Day\":false,\"qqqBelow200Day\":false,\"vix\":18,\"breadth50\":0.6,"
                        + "\"qqqMacdNegative\":false,\"qqqRsi\":55,\"narrowRally\":false,\"quality\":\"HEALTHY\"}");
    }

    @Test
    void changingAnyInputChangesTheEvidenceChecksum() {
        var inputs = List.of(
                baseline(),
                input(0.81, 0.7, 0.6, 0.9, false, false, 18, 0.6, false, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.71, 0.6, 0.9, false, false, 18, 0.6, false, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.61, 0.9, false, false, 18, 0.6, false, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.91, false, false, 18, 0.6, false, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.9, true, false, 18, 0.6, false, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.9, false, true, 18, 0.6, false, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.9, false, false, 19, 0.6, false, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.9, false, false, 18, 0.61, false, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.9, false, false, 18, 0.6, true, 55, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.9, false, false, 18, 0.6, false, 54, false, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.9, false, false, 18, 0.6, false, 55, true, EvidenceQuality.HEALTHY),
                input(0.8, 0.7, 0.6, 0.9, false, false, 18, 0.6, false, 55, false, EvidenceQuality.PARTIAL));

        assertThat(inputs.stream()
                        .map(CanonicalMarketRegimeEvidence::from)
                        .map(evidence -> evidence.checksum("3.0.0-draft", DATA_AS_OF))
                        .distinct())
                .hasSize(inputs.size());
    }

    private static MarketRegimeEngine.Input baseline() {
        return input(0.8, 0.7, 0.6, 0.9, false, false, 18, 0.6, false, 55, false, EvidenceQuality.HEALTHY);
    }

    private static MarketRegimeEngine.Input input(
            double trend,
            double momentum,
            double breadth,
            double stress,
            boolean spyBelow,
            boolean qqqBelow,
            double vix,
            double breadth50,
            boolean macdNegative,
            double rsi,
            boolean narrow,
            EvidenceQuality quality) {
        return new MarketRegimeEngine.Input(
                trend,
                momentum,
                breadth,
                stress,
                spyBelow,
                qqqBelow,
                vix,
                breadth50,
                macdNegative,
                rsi,
                narrow,
                quality);
    }
}
