package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.time.Instant;

public final class HoldingEvidenceReadiness {
    private HoldingEvidenceReadiness() {}

    public static AnalysisReadiness assess(HoldingEvidence evidence, Instant now, AnalysisFreshnessPolicy freshness) {
        if (!evidence.position().classificationConfirmed()
                || evidence.position().classification() == HoldingClassification.UNKNOWN) {
            return AnalysisReadiness.WAIT_FOR_CLASSIFICATION;
        }
        if (!evidence.instrument().active()) return AnalysisReadiness.BLOCKED;
        if (!evidence.quote().available()
                || evidence.completedBars().isEmpty()
                || evidence.completedBars().stream().noneMatch(HoldingEvidence.PriceBar::completed)
                || !evidence.indicators().trendAvailable()) {
            return AnalysisReadiness.WAIT_FOR_MARKET_DATA;
        }
        var policy = evidence.strategy().freshness();
        if (evidence.quality() == EvidenceQuality.STALE
                || freshness.stalePrice(evidence.quote().marketDate(), now, policy)) {
            return AnalysisReadiness.STALE;
        }
        return switch (evidence.position().classification()) {
            case CORE_BROAD_ETF, CORE_TECH_ETF ->
                evidence.profile().fundProfileAvailable() && evidence.drawdown().available()
                        ? etfReadiness(evidence, now, freshness)
                        : AnalysisReadiness.PARTIAL;
            case THEMATIC_ETF ->
                evidence.profile().fundProfileAvailable()
                                && evidence.profile().thematic()
                                && evidence.profile().liquidityStatus() != null
                                && evidence.profile().portfolioOverlap() != null
                        ? etfReadiness(evidence, now, freshness)
                        : AnalysisReadiness.PARTIAL;
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> qualityStockReadiness(evidence, now, freshness);
            case TACTICAL_STOCK, CYCLICAL_TACTICAL, TURNAROUND_TACTICAL ->
                tacticalStockReadiness(evidence, now, freshness);
            case SPECULATIVE ->
                evidence.stop().formalStop() != null && evidence.nextEvent().available()
                        ? tacticalReadiness(evidence, now, freshness)
                        : AnalysisReadiness.BLOCKED;
            case UNVESTED_COMPENSATION, CASH_EQUIVALENT -> AnalysisReadiness.BLOCKED;
            default -> qualityReadiness(evidence.quality());
        };
    }

    private static AnalysisReadiness tacticalStockReadiness(
            HoldingEvidence evidence, Instant now, AnalysisFreshnessPolicy freshness) {
        if (evidence.stop().formalStop() == null) return AnalysisReadiness.BLOCKED;
        if (!evidence.thesis().available()) return AnalysisReadiness.WAIT_FOR_CATALYST;
        if (evidence.thesis().invalidated()
                || (evidence.thesis().expiresAt() != null
                        && !evidence.thesis().expiresAt().isAfter(now))) {
            return AnalysisReadiness.BLOCKED;
        }
        if (!evidence.catalyst().available() || !evidence.catalyst().confirmedAt(now)) {
            return AnalysisReadiness.WAIT_FOR_CATALYST;
        }
        if (!evidence.nextEvent().available()
                || evidence.capitalQuality() != EvidenceQuality.HEALTHY
                || evidence.riskQuality() != EvidenceQuality.HEALTHY
                || evidence.clusterOpenRisk() == null
                || evidence.totalOpenRisk() == null) {
            return AnalysisReadiness.PARTIAL;
        }
        var policy = evidence.strategy().freshness();
        if (freshness.staleDays(evidence.stop().dataAsOf(), now, policy.macroDailyDays())
                || freshness.staleDays(evidence.catalyst().dataAsOf(), now, policy.earningsCalendarDays())
                || freshness.staleDays(evidence.nextEvent().dataAsOf(), now, policy.earningsCalendarDays())
                || freshness.staleDays(evidence.riskDataAsOf(), now, policy.macroDailyDays())) {
            return AnalysisReadiness.STALE;
        }
        return qualityReadiness(evidence.quality());
    }

    private static AnalysisReadiness qualityReadiness(EvidenceQuality quality) {
        return quality == EvidenceQuality.HEALTHY ? AnalysisReadiness.READY : AnalysisReadiness.PARTIAL;
    }

    private static AnalysisReadiness qualityStockReadiness(
            HoldingEvidence evidence, Instant now, AnalysisFreshnessPolicy freshness) {
        if (!evidence.fundamentals().available() || !evidence.valuation().available()) {
            return AnalysisReadiness.PARTIAL;
        }
        var policy = evidence.strategy().freshness();
        if (freshness.staleDays(evidence.fundamentals().dataAsOf(), now, policy.financialQuarterDays())
                || freshness.staleDays(evidence.valuation().dataAsOf(), now, policy.financialQuarterDays())) {
            return AnalysisReadiness.STALE;
        }
        if (!evidence.nextEvent().available() || evidence.fundamentals().estimateQuality() == EvidenceQuality.MISSING) {
            return AnalysisReadiness.PARTIAL;
        }
        return qualityReadiness(evidence, now, freshness);
    }

    private static AnalysisReadiness qualityReadiness(
            HoldingEvidence evidence, Instant now, AnalysisFreshnessPolicy freshness) {
        if (evidence.riskQuality() != EvidenceQuality.HEALTHY
                || evidence.clusterOpenRisk() == null
                || evidence.totalOpenRisk() == null
                || evidence.riskDataAsOf() == null) {
            return AnalysisReadiness.PARTIAL;
        }
        var policy = evidence.strategy().freshness();
        if (freshness.staleDays(evidence.fundamentals().dataAsOf(), now, policy.financialQuarterDays())
                || freshness.staleDays(evidence.valuation().dataAsOf(), now, policy.financialQuarterDays())
                || freshness.staleDays(evidence.fundamentals().estimateDataAsOf(), now, policy.estimatesDays())
                || freshness.staleDays(evidence.nextEvent().dataAsOf(), now, policy.earningsCalendarDays())
                || freshness.staleDays(evidence.riskDataAsOf(), now, policy.macroDailyDays())) {
            return AnalysisReadiness.STALE;
        }
        return qualityReadiness(evidence.quality());
    }

    private static AnalysisReadiness tacticalReadiness(
            HoldingEvidence evidence, Instant now, AnalysisFreshnessPolicy freshness) {
        if (!evidence.thesis().available()) return AnalysisReadiness.WAIT_FOR_CATALYST;
        var policy = evidence.strategy().freshness();
        if (freshness.staleDays(evidence.stop().dataAsOf(), now, policy.macroDailyDays())
                || freshness.staleDays(evidence.nextEvent().dataAsOf(), now, policy.earningsCalendarDays())
                || freshness.staleDays(evidence.riskDataAsOf(), now, policy.macroDailyDays())) {
            return AnalysisReadiness.STALE;
        }
        return evidence.thesis().available() && !evidence.thesis().invalidated()
                ? AnalysisReadiness.PARTIAL
                : AnalysisReadiness.BLOCKED;
    }

    private static AnalysisReadiness etfReadiness(
            HoldingEvidence evidence, Instant now, AnalysisFreshnessPolicy freshness) {
        var policy = evidence.strategy().freshness();
        if (freshness.staleDays(evidence.profile().dataAsOf(), now, policy.etfProfileDays())
                || freshness.staleDays(evidence.regime().dataAsOf(), now, policy.macroDailyDays())
                || freshness.staleDays(evidence.riskDataAsOf(), now, policy.macroDailyDays())) {
            return AnalysisReadiness.STALE;
        }
        return qualityReadiness(evidence.quality());
    }
}
