package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BehavioralEvidenceFreshnessTest {
    private static final Instant OPENED = Instant.parse("2026-01-01T20:00:00Z");
    private static final Instant LAST_DECISION = Instant.parse("2026-02-01T20:00:00Z");
    private static final Instant LAST_ADD = Instant.parse("2026-03-01T20:00:00Z");

    @Test
    void lastAddTakesPrecedenceAsTheEvidenceReferenceTime() {
        assertThat(BehavioralEvidenceService.referenceTime(LAST_ADD, LAST_DECISION, OPENED))
                .isEqualTo(LAST_ADD);
        assertThat(BehavioralEvidenceService.referenceTime(null, LAST_DECISION, OPENED))
                .isEqualTo(LAST_DECISION);
        assertThat(BehavioralEvidenceService.referenceTime(null, null, OPENED)).isEqualTo(OPENED);
    }

    @Test
    void oldValuationEvidenceBeforeLastAddCannotUnlockAveragingDown() {
        var oldValuation = LAST_ADD.minusSeconds(1);

        assertThat(BehavioralEvidenceService.independentNewEvidence(oldValuation, LAST_ADD))
                .isFalse();
    }

    @Test
    void newCatalystOrFundamentalEvidenceAfterLastAddSatisfiesTheGate() {
        var newCatalyst = LAST_ADD.plusSeconds(1);
        var newFundamentals = LAST_ADD.plusSeconds(3600);

        assertThat(BehavioralEvidenceService.independentNewEvidence(newCatalyst, LAST_ADD))
                .isTrue();
        assertThat(BehavioralEvidenceService.independentNewEvidence(newFundamentals, LAST_ADD))
                .isTrue();
    }

    @Test
    void missingTimestampCannotBeReplacedByAnAcknowledgementTag() {
        assertThat(BehavioralEvidenceService.independentNewEvidence(null, LAST_ADD))
                .isFalse();
    }
}
