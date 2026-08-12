package com.example.portfolio.analysis.decision;

import com.example.portfolio.analysis.allocation.SleeveAllocation;
import com.example.portfolio.analysis.dip.EtfDipDecisionEvent;
import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import java.math.BigDecimal;
import java.time.Instant;

public record DecisionContext(
        HoldingEvidence evidence,
        AnalysisReadiness readiness,
        BigDecimal targetMin,
        BigDecimal targetMax,
        BigDecimal normalMax,
        BigDecimal hardMax,
        SleeveAllocation sleeveAllocation,
        EtfDipDecisionEvent dipEvent,
        Instant lastDecisionAt,
        Instant lastAddAt,
        boolean averagingDown,
        boolean thesisImproving,
        boolean anchoredToCostBasis,
        int holdingTradingDays,
        boolean thesisProgress,
        Instant ideaCooldownUntil,
        Instant decisionAt) {
    public DecisionContext(
            HoldingEvidence evidence,
            AnalysisReadiness readiness,
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal normalMax,
            BigDecimal hardMax) {
        this(
                evidence,
                readiness,
                targetMin,
                targetMax,
                normalMax,
                hardMax,
                null,
                null,
                null,
                null,
                false,
                false,
                false,
                0,
                false,
                null,
                evidence.dataAsOf());
    }

    public DecisionContext(
            HoldingEvidence evidence,
            AnalysisReadiness readiness,
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal normalMax,
            BigDecimal hardMax,
            SleeveAllocation sleeveAllocation) {
        this(
                evidence,
                readiness,
                targetMin,
                targetMax,
                normalMax,
                hardMax,
                sleeveAllocation,
                null,
                null,
                null,
                false,
                false,
                false,
                0,
                false,
                null,
                evidence.dataAsOf());
    }

    public DecisionContext(
            HoldingEvidence evidence,
            AnalysisReadiness readiness,
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal normalMax,
            BigDecimal hardMax,
            SleeveAllocation sleeveAllocation,
            EtfDipDecisionEvent dipEvent) {
        this(
                evidence,
                readiness,
                targetMin,
                targetMax,
                normalMax,
                hardMax,
                sleeveAllocation,
                dipEvent,
                null,
                null,
                false,
                false,
                false,
                0,
                false,
                null,
                evidence.dataAsOf());
    }
}
