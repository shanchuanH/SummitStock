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
                || !evidence.indicators().trendAvailable()) {
            return AnalysisReadiness.WAIT_FOR_MARKET_DATA;
        }
        if (evidence.quality() == EvidenceQuality.STALE || freshness.stale(evidence.dataAsOf(), now)) {
            return AnalysisReadiness.STALE;
        }
        return switch (evidence.position().classification()) {
            case CORE_BROAD_ETF, CORE_TECH_ETF ->
                evidence.profile().fundProfileAvailable() && evidence.drawdown().available()
                        ? qualityReadiness(evidence.quality())
                        : AnalysisReadiness.PARTIAL;
            case THEMATIC_ETF ->
                evidence.profile().fundProfileAvailable()
                                && evidence.profile().thematic()
                                && evidence.profile().liquidityStatus() != null
                                && evidence.profile().portfolioOverlap() != null
                        ? qualityReadiness(evidence.quality())
                        : AnalysisReadiness.PARTIAL;
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL ->
                evidence.fundamentals().available()
                                && evidence.valuation().available()
                                && evidence.nextEvent().available()
                                && evidence.thesis().available()
                        ? qualityReadiness(evidence.quality())
                        : AnalysisReadiness.WAIT_FOR_FUNDAMENTALS;
            case SPECULATIVE ->
                evidence.stop().formalStop() != null && evidence.nextEvent().available()
                        ? AnalysisReadiness.PARTIAL
                        : AnalysisReadiness.BLOCKED;
            case UNVESTED_COMPENSATION, CASH_EQUIVALENT -> AnalysisReadiness.BLOCKED;
            default -> qualityReadiness(evidence.quality());
        };
    }

    private static AnalysisReadiness qualityReadiness(EvidenceQuality quality) {
        return quality == EvidenceQuality.HEALTHY ? AnalysisReadiness.READY : AnalysisReadiness.PARTIAL;
    }
}
