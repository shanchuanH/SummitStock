package com.example.portfolio.analysis.decision;

import com.example.portfolio.analysis.allocation.SleeveAllocation;
import com.example.portfolio.analysis.dip.EtfDipDecisionEvent;
import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import java.math.BigDecimal;

public record DecisionContext(
        HoldingEvidence evidence,
        AnalysisReadiness readiness,
        BigDecimal targetMin,
        BigDecimal targetMax,
        BigDecimal normalMax,
        BigDecimal hardMax,
        SleeveAllocation sleeveAllocation,
        EtfDipDecisionEvent dipEvent) {
    public DecisionContext(
            HoldingEvidence evidence,
            AnalysisReadiness readiness,
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal normalMax,
            BigDecimal hardMax) {
        this(evidence, readiness, targetMin, targetMax, normalMax, hardMax, null, null);
    }

    public DecisionContext(
            HoldingEvidence evidence,
            AnalysisReadiness readiness,
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal normalMax,
            BigDecimal hardMax,
            SleeveAllocation sleeveAllocation) {
        this(evidence, readiness, targetMin, targetMax, normalMax, hardMax, sleeveAllocation, null);
    }
}
