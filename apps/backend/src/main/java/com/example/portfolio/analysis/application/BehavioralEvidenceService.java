package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.market.provider.TradingCalendar;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
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
                          (SELECT ra.rationale FROM recommendation_acknowledgement ra
                           JOIN recommendation r ON r.id=ra.recommendation_id
                           WHERE r.position_id=p.id AND ra.rationale IS NOT NULL
                           ORDER BY ra.acknowledged_at DESC LIMIT 1) lastRationale,
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
        var averagingDown = row.averageCost() != null && last != null && last.compareTo(row.averageCost()) < 0;
        var thesisImproving = row.thesisProgress()
                || (row.lastEvidenceAt() != null
                        && row.confirmedAt() != null
                        && row.lastEvidenceAt().isAfter(row.confirmedAt()));
        var completed = calendar.latestCompletedSession(now);
        var holdingDays = calendar.sessionsBetween(row.openedAt().toLocalDate(), completed);
        return new BehavioralEvidence(
                instant(row.lastDecisionAt()),
                instant(row.lastAddAt()),
                averagingDown,
                thesisImproving,
                anchored(row.lastRationale()),
                holdingDays,
                row.thesisProgress(),
                instant(row.ideaCooldownUntil()));
    }

    private static boolean anchored(String rationale) {
        if (rationale == null) return false;
        var value = rationale.toLowerCase(Locale.ROOT);
        return value.contains("cost basis") || value.contains("break even") || value.contains("breakeven");
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private record BehaviorRow(
            LocalDateTime openedAt,
            BigDecimal averageCost,
            LocalDateTime lastDecisionAt,
            LocalDateTime lastAddAt,
            String lastRationale,
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
