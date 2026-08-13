package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class HoldingEvidenceReadinessTest {
    private final AnalysisFreshnessPolicy freshness = new AnalysisFreshnessPolicy();

    @Test
    void qualityStockWaitsForFundamentalsButSpeculativeCanRemainPartial() {
        var googl = HoldingEvidenceFixtures.withFundamentals(
                HoldingEvidenceFixtures.evidence("GOOGL", "EQUITY", HoldingClassification.QUALITY_STOCK), false);
        var dxyz = HoldingEvidenceFixtures.withFundamentals(
                HoldingEvidenceFixtures.evidence("DXYZ", "EQUITY", HoldingClassification.SPECULATIVE), false);
        assertThat(HoldingEvidenceReadiness.assess(googl, googl.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.WAIT_FOR_FUNDAMENTALS);
        assertThat(HoldingEvidenceReadiness.assess(dxyz, dxyz.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.PARTIAL);
    }

    @Test
    void freshQuoteDoesNotHideStaleFundamentals() {
        var current = HoldingEvidenceFixtures.evidence("GOOGL", "EQUITY", HoldingClassification.QUALITY_STOCK);
        var staleFundamentals = new HoldingEvidence.FundamentalSnapshot(
                true,
                EvidenceQuality.HEALTHY,
                current.dataAsOf().minus(Duration.ofDays(250)),
                "HEALTHY",
                "UP",
                EvidenceQuality.HEALTHY,
                current.dataAsOf());
        var evidence = new HoldingEvidence(
                current.position(),
                current.instrument(),
                current.portfolioEquity(),
                current.trackedCash(),
                current.emergencyCash(),
                current.tacticalReserve(),
                current.currentWeight(),
                current.clusterWeight(),
                current.clusterOpenRisk(),
                current.totalOpenRisk(),
                current.quote(),
                current.completedBars(),
                current.indicators(),
                staleFundamentals,
                current.valuation(),
                current.nextEvent(),
                current.catalyst(),
                current.thesis(),
                current.regime(),
                current.drawdown(),
                current.stop(),
                current.profile(),
                current.capitalQuality(),
                current.riskQuality(),
                current.riskDataAsOf(),
                current.providerHardError(),
                current.quality(),
                current.strategy(),
                current.dataAsOf());

        assertThat(HoldingEvidenceReadiness.assess(evidence, current.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.STALE);
    }
}
