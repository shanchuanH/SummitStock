package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import org.junit.jupiter.api.Test;

class EtfAnalysisPolicyTest {
    @Test
    void thematicEtfDoesNotUseCompanyFundamentalsOrEarningsTemplate() {
        var dram = HoldingEvidenceFixtures.evidence("DRAM", "ETF", HoldingClassification.THEMATIC_ETF);
        dram = HoldingEvidenceFixtures.withFundamentals(dram, false);
        dram = HoldingEvidenceFixtures.withEvent(dram, false);
        assertThat(HoldingEvidenceReadiness.assess(dram, dram.dataAsOf(), new AnalysisFreshnessPolicy()))
                .isEqualTo(AnalysisReadiness.READY);
    }
}
