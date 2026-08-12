package com.example.portfolio.analysis.dip;

import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record EtfDipDecisionEvent(
        String state,
        double setupScore,
        int triggerCount,
        Integer trancheIndex,
        BigDecimal tranchePct,
        LocalDate cooldownUntilMarketDate,
        BigDecimal reserveBefore,
        BigDecimal reserveAfter,
        EvidenceQuality quality,
        Instant dataAsOf) {
    public boolean readyForNextTranche() {
        return state != null && state.startsWith("READY_FOR_TRANCHE_") && trancheIndex != null && tranchePct != null;
    }
}
