package com.example.portfolio.macro;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class VolatilityContextEngineTest {
    private final VolatilityContextEngine engine = new VolatilityContextEngine();

    @Test
    void computesBroadAndTechnologyVolatilityWithoutInventingEvidence() {
        var result = engine.evaluate(new VolatilityContextEngine.Input(
                decimal("20"),
                decimals("14", "15", "16", "17", "18", "20"),
                decimal("19"),
                decimal("27"),
                decimals("19", "20", "21", "22", "23", "27"),
                decimal("0.95"),
                decimal("1.00"),
                decimal("1.25")));

        assertThat(result.vixPercentile()).isEqualByComparingTo("1");
        assertThat(result.vixDelta1d()).isEqualByComparingTo("2");
        assertThat(result.vixDelta2d()).isEqualByComparingTo("3");
        assertThat(result.vixDelta5d()).isEqualByComparingTo("6");
        assertThat(result.vixTermState()).isEqualTo(VolatilityContextEngine.TermState.BACKWARDATION);
        assertThat(result.vxnVixRatio()).isEqualByComparingTo("1.35");
        assertThat(result.vxnVixSpread()).isEqualByComparingTo("7");
        assertThat(result.techStressState()).isEqualTo(VolatilityContextEngine.TechStressState.ELEVATED);
    }

    @Test
    void classifiesTermStructureFromConfiguredResearchBoundaries() {
        assertThat(evaluateTerm("18", "20")).isEqualTo(VolatilityContextEngine.TermState.CONTANGO);
        assertThat(evaluateTerm("19", "20")).isEqualTo(VolatilityContextEngine.TermState.FLAT);
        assertThat(evaluateTerm("20", "20")).isEqualTo(VolatilityContextEngine.TermState.BACKWARDATION);
    }

    @Test
    void missingVxnDoesNotSuppressBroadVolatilityContext() {
        var result = engine.evaluate(new VolatilityContextEngine.Input(
                decimal("18"),
                decimals("16", "17", "18"),
                decimal("20"),
                null,
                List.of(),
                decimal("0.95"),
                decimal("1.00"),
                decimal("1.25")));

        assertThat(result.vix()).isEqualByComparingTo("18");
        assertThat(result.vixTermState()).isEqualTo(VolatilityContextEngine.TermState.CONTANGO);
        assertThat(result.vxn()).isNull();
        assertThat(result.vxnVixRatio()).isNull();
        assertThat(result.techStressState()).isEqualTo(VolatilityContextEngine.TechStressState.MISSING);
    }

    @Test
    void localFixtureNeverSimulatesVxnEvidence() {
        var provider = new FakeMacroDataProvider(Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));

        var result = provider.fetch("VXNCLS", LocalDate.parse("2026-08-01"), LocalDate.parse("2026-08-20"));

        assertThat(result.observations()).isEmpty();
        assertThat(result.quality().name()).isEqualTo("MISSING");
    }

    private VolatilityContextEngine.TermState evaluateTerm(String vix, String vix3m) {
        return engine.evaluate(new VolatilityContextEngine.Input(
                        decimal(vix),
                        decimals(vix),
                        decimal(vix3m),
                        null,
                        List.of(),
                        decimal("0.95"),
                        decimal("1.00"),
                        decimal("1.25")))
                .vixTermState();
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }

    private static List<BigDecimal> decimals(String... values) {
        return java.util.Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
