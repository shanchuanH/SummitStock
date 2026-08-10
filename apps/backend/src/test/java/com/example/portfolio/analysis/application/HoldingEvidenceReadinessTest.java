package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
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
}
