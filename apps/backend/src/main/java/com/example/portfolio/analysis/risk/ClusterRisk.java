package com.example.portfolio.analysis.risk;

import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ClusterRisk(
        UUID clusterId,
        BigDecimal openRiskAmount,
        BigDecimal openRiskFraction,
        int memberCount,
        EvidenceQuality quality,
        Instant dataAsOf) {
    public static ClusterRisk none() {
        return new ClusterRisk(null, BigDecimal.ZERO, BigDecimal.ZERO, 0, EvidenceQuality.MISSING, null);
    }
}
