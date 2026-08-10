package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.decision.BehavioralFirewall;
import com.example.portfolio.analysis.decision.DecisionContext;
import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.RuleIds;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BehavioralFirewallTest {
    private static final Instant NOW = Instant.parse("2026-08-05T20:00:00Z");
    private final BehavioralFirewall firewall = new BehavioralFirewall();

    @Test
    void coolingBlocksNewCapitalButProducesNoRiskReductionCandidate() {
        var values = firewall.evaluate(context(
                HoldingClassification.QUALITY_STOCK, NOW.minusSeconds(3600), false, false, false, 10, false, null));

        assertThat(values).singleElement().satisfies(value -> {
            assertThat(value.action()).isEqualTo(RecommendationAction.DO_NOT_ADD);
            assertThat(value.ruleId()).isEqualTo(RuleIds.RISK_COOLING_PERIOD);
        });
    }

    @Test
    void averagingDownWithoutImprovementAndCostAnchoringAreBlocked() {
        var values = firewall.evaluate(
                context(HoldingClassification.QUALITY_STOCK, null, true, false, true, 10, false, null));

        assertThat(values)
                .extracting(value -> value.ruleId())
                .containsExactlyInAnyOrder(RuleIds.RISK_INVALID_AVERAGING, RuleIds.RISK_ANCHORING);
        assertThat(values).allMatch(value -> value.action() == RecommendationAction.DO_NOT_ADD);
    }

    @Test
    void expiredSpeculativeTimeStopForcesExitWithoutThesisProgress() {
        var values = firewall.evaluate(
                context(HoldingClassification.SPECULATIVE, null, false, false, false, 60, false, null));

        assertThat(values).singleElement().satisfies(value -> {
            assertThat(value.action()).isEqualTo(RecommendationAction.EXIT);
            assertThat(value.ruleId()).isEqualTo(RuleIds.SPECULATIVE_TIME_STOP);
        });
    }

    @Test
    void socialIdeaCooldownBlocksNewCapitalUntilItsExplicitDeadline() {
        var values = firewall.evaluate(context(
                HoldingClassification.QUALITY_STOCK, null, false, false, false, 10, false, NOW.plusSeconds(7200)));

        assertThat(values).singleElement().extracting(value -> value.ruleId()).isEqualTo(RuleIds.RISK_COOLING_PERIOD);
    }

    private static DecisionContext context(
            HoldingClassification classification,
            Instant lastDecisionAt,
            boolean averagingDown,
            boolean thesisImproving,
            boolean anchored,
            int holdingDays,
            boolean thesisProgress,
            Instant ideaCooldownUntil) {
        var evidence = HoldingEvidenceFixtures.evidence("TEST", "EQUITY", classification);
        return new DecisionContext(
                evidence,
                AnalysisReadiness.READY,
                evidence.strategy().quality().targetMin(),
                evidence.strategy().quality().targetMax(),
                evidence.strategy().quality().normalMax(),
                evidence.strategy().quality().hardMax(),
                null,
                null,
                lastDecisionAt,
                null,
                averagingDown,
                thesisImproving,
                anchored,
                holdingDays,
                thesisProgress,
                ideaCooldownUntil,
                NOW);
    }
}
