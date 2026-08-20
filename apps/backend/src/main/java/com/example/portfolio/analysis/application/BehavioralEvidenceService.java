package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.market.provider.TradingCalendar;
import com.example.portfolio.portfolio.DecisionReasonTag;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public final class BehavioralEvidenceService {
    private final JdbcClient jdbc;
    private final TradingCalendar calendar;

    public BehavioralEvidenceService(JdbcClient jdbc, TradingCalendar calendar) {
        this.jdbc = jdbc;
        this.calendar = calendar;
    }

    public BehavioralEvidence load(HoldingEvidence evidence, Instant now) {
        var row = jdbc.sql(
                        """
                        SELECT p.opened_at openedAt,p.average_cost averageCost,
                          (SELECT MAX(ra.acknowledged_at) FROM recommendation_acknowledgement ra
                           JOIN recommendation r ON r.id=ra.recommendation_id
                           WHERE r.position_id=p.id) lastDecisionAt,
                          (SELECT MAX(ra.acknowledged_at) FROM recommendation_acknowledgement ra
                           JOIN recommendation r ON r.id=ra.recommendation_id
                           WHERE r.position_id=p.id AND r.action IN ('ADD','STARTER_BUY','DEPLOY_DIP_TRANCHE')) lastAddAt,
                          (SELECT ra.reason_tags FROM recommendation_acknowledgement ra
                           JOIN recommendation r ON r.id=ra.recommendation_id
                           WHERE r.position_id=p.id AND ra.reason_tags IS NOT NULL
                           ORDER BY ra.acknowledged_at DESC LIMIT 1) lastReasonTags,
                          (SELECT ra.reference_price FROM recommendation_acknowledgement ra
                           JOIN recommendation r ON r.id=ra.recommendation_id
                           WHERE r.position_id=p.id AND r.action IN ('ADD','STARTER_BUY')
                             AND ra.reference_price IS NOT NULL
                           ORDER BY ra.acknowledged_at DESC LIMIT 1) lastAddReferencePrice,
                          COALESCE(t.thesis_progress,FALSE) thesisProgress,
                          t.confirmed_at confirmedAt,t.last_evidence_at lastEvidenceAt,
                          (SELECT MAX(i.cooldown_until) FROM investment_idea i
                           JOIN investment_account a ON a.user_id=i.user_id
                           WHERE a.id=p.account_id AND i.instrument_id=p.instrument_id) ideaCooldownUntil
                        FROM position p LEFT JOIN position_thesis t ON t.position_id=p.id
                        WHERE p.id=UUID_TO_BIN(:positionId)
                        """)
                .param("positionId", evidence.position().id().toString())
                .query(BehaviorRow.class)
                .single();
        var last = evidence.quote().last();
        var reference = row.lastAddReferencePrice() != null ? row.lastAddReferencePrice() : row.averageCost();
        var proposedEntryBelowReference = reference != null && last != null && last.compareTo(reference) < 0;
        var reasonTags = DecisionReasonTags.parse(row.lastReasonTags());
        var thesisImproving = row.thesisProgress()
                || (row.lastEvidenceAt() != null
                        && row.confirmedAt() != null
                        && row.lastEvidenceAt().isAfter(row.confirmedAt()))
                || reasonTags.stream().anyMatch(BehavioralEvidenceService::independentEvidence);
        var completed = calendar.latestCompletedSession(now);
        var holdingDays = calendar.sessionsBetween(row.openedAt().toLocalDate(), completed);
        return new BehavioralEvidence(
                instant(row.lastDecisionAt()),
                instant(row.lastAddAt()),
                proposedEntryBelowReference,
                thesisImproving,
                reasonTags.contains(DecisionReasonTag.COST_BASIS_ANCHOR),
                holdingDays,
                row.thesisProgress(),
                instant(row.ideaCooldownUntil()));
    }

    private static boolean independentEvidence(DecisionReasonTag tag) {
        return Set.of(
                        DecisionReasonTag.NEW_FUNDAMENTAL_EVIDENCE,
                        DecisionReasonTag.VALUATION,
                        DecisionReasonTag.PRICE_CONFIRMATION,
                        DecisionReasonTag.CATALYST)
                .contains(tag);
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record BehaviorRow(
            LocalDateTime openedAt,
            BigDecimal averageCost,
            LocalDateTime lastDecisionAt,
            LocalDateTime lastAddAt,
            String lastReasonTags,
            BigDecimal lastAddReferencePrice,
            boolean thesisProgress,
            LocalDateTime confirmedAt,
            LocalDateTime lastEvidenceAt,
            LocalDateTime ideaCooldownUntil) {}

    public record BehavioralEvidence(
            Instant lastDecisionAt,
            Instant lastAddAt,
            boolean averagingDown,
            boolean thesisImproving,
            boolean anchoredToCostBasis,
            int holdingTradingDays,
            boolean thesisProgress,
            Instant ideaCooldownUntil) {}
}
