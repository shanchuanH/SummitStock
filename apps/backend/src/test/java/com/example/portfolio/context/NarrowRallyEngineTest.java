package com.example.portfolio.context;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class NarrowRallyEngineTest {
    private final NarrowRallyEngine engine = new NarrowRallyEngine();

    @Test
    void identifiesStrongIndexesWithDeterioratingParticipationAndEqualWeightLag() {
        var result = engine.evaluate(input(true, true, "0.55", "0.62", "0.64", "0.65", "0.04", "0.08", "-0.03"));

        assertThat(result.complete()).isTrue();
        assertThat(result.breadthDeteriorating()).isTrue();
        assertThat(result.newHighsDeteriorating()).isTrue();
        assertThat(result.equalWeightLagging()).isTrue();
        assertThat(result.narrowRally()).isTrue();
    }

    @Test
    void doesNotCallBroadParticipationAConcentratedRally() {
        var result = engine.evaluate(input(true, true, "0.70", "0.66", "0.64", "0.65", "0.10", "0.08", "0.02"));

        assertThat(result.weakParticipationSignals()).isZero();
        assertThat(result.narrowRally()).isFalse();
    }

    @Test
    void missingOrWeakIndexEvidenceFailsClosed() {
        var missing = engine.evaluate(new NarrowRallyEngine.Input(
                true, true, decimal("0.55"), decimal("0.60"), null, null, null, null, null));
        var weakIndex = engine.evaluate(input(false, true, "0.55", "0.62", "0.64", "0.65", "0.04", "0.08", "-0.03"));

        assertThat(missing.complete()).isFalse();
        assertThat(missing.narrowRally()).isFalse();
        assertThat(weakIndex.narrowRally()).isFalse();
    }

    private static NarrowRallyEngine.Input input(
            boolean spyAbove,
            boolean qqqAbove,
            String breadth50,
            String breadth200,
            String priorBreadth50,
            String priorBreadth200,
            String newHighs,
            String priorNewHighs,
            String relativeReturn) {
        return new NarrowRallyEngine.Input(
                spyAbove,
                qqqAbove,
                decimal(breadth50),
                decimal(breadth200),
                decimal(priorBreadth50),
                decimal(priorBreadth200),
                decimal(newHighs),
                decimal(priorNewHighs),
                decimal(relativeReturn));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
