package com.example.portfolio.analysis.decision;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.strategy.market.EvidenceQuality;

public enum NewRiskReadiness {
    READY,
    PARTIAL,
    BLOCKED;

    public static NewRiskReadiness assess(DecisionContext context) {
        if (context.readiness() != AnalysisReadiness.READY) {
            return context.readiness() == AnalysisReadiness.PARTIAL ? PARTIAL : BLOCKED;
        }
        var evidence = context.evidence();
        return evidence.quality() == EvidenceQuality.HEALTHY
                        && evidence.capitalQuality() == EvidenceQuality.HEALTHY
                        && evidence.riskQuality() == EvidenceQuality.HEALTHY
                        && evidence.quote().quality() == EvidenceQuality.HEALTHY
                ? READY
                : PARTIAL;
    }
}
