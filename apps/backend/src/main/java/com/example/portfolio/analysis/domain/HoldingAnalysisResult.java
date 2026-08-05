package com.example.portfolio.analysis.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HoldingAnalysisResult(
        UUID positionId,
        String analysisStatus,
        AnalysisReadiness readiness,
        String confidence,
        BigDecimal currentWeight,
        BigDecimal targetWeightMin,
        BigDecimal targetWeightMax,
        boolean exactQuantityAllowed,
        RecommendationAction recommendedAction,
        BigDecimal recommendedQuantityMin,
        BigDecimal recommendedQuantityMax,
        List<String> reasons,
        List<String> risks,
        List<String> changeConditions,
        List<String> ruleIds,
        String strategyVersion,
        String configHash,
        Instant dataAsOf,
        Instant validUntil,
        String evidenceChecksum) {
    public HoldingAnalysisResult {
        reasons = List.copyOf(reasons);
        risks = List.copyOf(risks);
        changeConditions = List.copyOf(changeConditions);
        ruleIds = List.copyOf(ruleIds);
    }
}
